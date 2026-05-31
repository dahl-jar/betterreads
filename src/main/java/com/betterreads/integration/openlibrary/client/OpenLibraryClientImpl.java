package com.betterreads.integration.openlibrary.client;

import java.util.Optional;
import java.util.function.Consumer;

import com.betterreads.catalog.service.BookFieldSource;
import com.betterreads.catalog.service.SourceBook;
import com.betterreads.common.util.LogSanitizer;
import com.betterreads.common.util.TextMatch;
import com.betterreads.integration.openlibrary.OpenLibraryClient;
import com.betterreads.integration.openlibrary.dto.SearchDoc;
import com.betterreads.integration.openlibrary.dto.SearchResponse;
import com.betterreads.integration.openlibrary.dto.WorkDetail;
import com.betterreads.integration.openlibrary.mapper.OpenLibraryMapper;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriBuilder;

/**
 * OpenLibrary REST API client.
 *
 * <p>A lookup is two calls: {@code search.json} resolves the work, then {@code /works/{key}.json}
 * adds the description and subjects the search omits. The search ranks fuzzily and can return a
 * related work, so {@link #titleMatches} rejects drift. 4xx resolves to {@link Optional#empty()};
 * 5xx and network failures propagate.
 */
@Component
public class OpenLibraryClientImpl implements OpenLibraryClient {

    private static final Logger LOG = LoggerFactory.getLogger(OpenLibraryClientImpl.class);

    private static final String SEARCH_PATH = "/search.json";

    private static final String WORK_PATH = "/works/{workKey}.json";

    private static final String SEARCH_FIELDS =
        "key,title,subtitle,author_name,first_publish_year,cover_i,isbn,language";

    private static final String WORKS_PREFIX = "/works/";

    private final WebClient openLibraryWebClient;

    private final OpenLibraryMapper mapper;

    public OpenLibraryClientImpl(
        final WebClient openLibraryWebClient,
        final OpenLibraryMapper mapper
    ) {
        this.openLibraryWebClient = openLibraryWebClient;
        this.mapper = mapper;
    }

    @Override
    public BookFieldSource source() {
        return BookFieldSource.OPEN_LIBRARY;
    }

    @Override
    public Optional<SourceBook> fetchByIsbn(final String isbn) {
        return search(builder -> builder.queryParam("q", "isbn:" + isbn))
            .flatMap(this::enrichAndMap);
    }

    @Override
    public Optional<SourceBook> fetchByTitleAuthor(final String title, final String author) {
        return search(builder -> builder.queryParam("title", title).queryParam("author", author))
            .filter(doc -> titleMatches(doc, title))
            .flatMap(this::enrichAndMap);
    }

    @Override
    public Optional<SourceBook> fetchByWorkKey(final String workKey) {
        final WorkDetail work = fetchWork(workKey);
        if (work == null) {
            return Optional.empty();
        }
        final SearchDoc doc = new SearchDoc(
            WORKS_PREFIX + workKey, work.title(), null, null, null, null, null, null);
        return Optional.ofNullable(mapper.toSourceBook(doc, work));
    }

    private Optional<SourceBook> enrichAndMap(final SearchDoc doc) {
        final String workKey = stripWorksPrefix(doc.key());
        final WorkDetail work = workKey == null ? null : fetchWork(workKey);
        return Optional.ofNullable(mapper.toSourceBook(doc, work));
    }

    private Optional<SearchDoc> search(final Consumer<UriBuilder> queryCustomizer) {
        try {
            final SearchResponse response = openLibraryWebClient.get()
                .uri(builder -> {
                    builder.path(SEARCH_PATH)
                        .queryParam("limit", 1)
                        .queryParam("fields", SEARCH_FIELDS);
                    queryCustomizer.accept(builder);
                    return builder.build();
                })
                .retrieve()
                .bodyToMono(SearchResponse.class)
                .block();
            if (response == null || response.docs() == null || response.docs().isEmpty()) {
                return Optional.empty();
            }
            return Optional.ofNullable(response.docs().get(0));
        } catch (WebClientResponseException ex) {
            if (ex.getStatusCode().is4xxClientError()) {
                LOG.debug("OpenLibrary search returned 4xx status={}", ex.getStatusCode().value());
                return Optional.empty();
            }
            throw ex;
        }
    }

    private @Nullable WorkDetail fetchWork(final String workKey) {
        try {
            return openLibraryWebClient.get()
                .uri(WORK_PATH, workKey)
                .retrieve()
                .bodyToMono(WorkDetail.class)
                .block();
        } catch (WebClientResponseException ex) {
            if (ex.getStatusCode().is4xxClientError()) {
                LOG.debug("OpenLibrary work fetch returned 4xx workKey={} status={}",
                    LogSanitizer.forLog(workKey), ex.getStatusCode().value());
                return null;
            }
            throw ex;
        }
    }

    /**
     * Returns true if the returned title equals the query or the query is the more specific string.
     *
     * <p>A title that extends the query is a different work and is rejected:
     * {@code "the sandman - overture"} does not match a query of {@code "the sandman"}.
     */
    private static boolean titleMatches(final SearchDoc doc, final String queryTitle) {
        final String docTitle = doc.title();
        if (docTitle == null) {
            return false;
        }
        final String trimmedDoc = docTitle.trim();
        final String trimmedQuery = queryTitle.trim();
        return trimmedDoc.equalsIgnoreCase(trimmedQuery)
            || TextMatch.containsIgnoreCase(trimmedQuery, trimmedDoc);
    }

    private static @Nullable String stripWorksPrefix(final @Nullable String key) {
        if (key == null) {
            return null;
        }
        return key.startsWith(WORKS_PREFIX) ? key.substring(WORKS_PREFIX.length()) : key;
    }
}
