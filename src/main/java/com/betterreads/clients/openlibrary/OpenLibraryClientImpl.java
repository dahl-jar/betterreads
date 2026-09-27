package com.betterreads.clients.openlibrary;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.SourceBook;
import com.betterreads.clients.http.WebClients;
import com.betterreads.text.TextMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;

/**
 * {@code search.json} has no description or subjects, so a lookup also fetches
 * {@code /works/{key}.json}. The search ranks fuzzily and can return a related work, so a hit whose
 * title drifts from the query is dropped. 4xx resolves to empty, 5xx and network errors propagate.
 */
@Component
class OpenLibraryClientImpl implements OpenLibraryClient {

    private static final Logger LOG = LoggerFactory.getLogger(OpenLibraryClientImpl.class);

    private static final String SEARCH_PATH = "/search.json";

    private static final String WORK_PATH = "/works/{workKey}.json";

    private static final String SEARCH_FIELDS =
        "key,title,subtitle,author_name,first_publish_year,cover_i,language";

    private static final String LIMIT_PARAM = "limit";

    private static final int CANDIDATE_LIMIT = 10;

    private static final String FIELDS_PARAM = "fields";

    private final WebClient openLibraryWebClient;

    private final OpenLibraryMapper mapper;

    OpenLibraryClientImpl(
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
        return searchDocs(1, builder -> builder.queryParam("q", "isbn:" + isbn)).stream()
            .findFirst()
            .flatMap(this::fetchWithWork);
    }

    @Override
    public Optional<SourceBook> fetchByTitleAuthor(final String title, final String author) {
        return searchDocs(CANDIDATE_LIMIT,
                builder -> builder.queryParam("title", title).queryParam("author", author))
            .stream()
            .filter(doc -> titleMatches(doc, title))
            .min(Comparator.comparing(OpenLibrarySearchDoc::firstPublishYear,
                Comparator.nullsLast(Comparator.naturalOrder())))
            .flatMap(this::fetchWithWork);
    }

    @Override
    public List<SourceBook> search(final String query, final int limit) {
        return searchDocs(limit, builder -> builder.queryParam("q", query)).stream()
            .map(doc -> mapper.toSourceBook(doc, null))
            .filter(book -> book != null)
            .toList();
    }

    @Override
    public Optional<SourceBook> fetchByWorkKey(final String workKey) {
        return fetchWork(workKey)
            .map(work -> mapper.toSourceBook(
                new OpenLibrarySearchDoc(workKey, work.title(), null, null, null, null, null), work));
    }

    private Optional<SourceBook> fetchWithWork(final OpenLibrarySearchDoc doc) {
        final OpenLibraryWork work = Optional.ofNullable(OpenLibraryMapper.stripWorksPrefix(doc.key()))
            .flatMap(this::fetchWork)
            .orElse(null);
        return Optional.ofNullable(mapper.toSourceBook(doc, work));
    }

    private List<OpenLibrarySearchDoc> searchDocs(final int limit, final Consumer<UriBuilder> queryCustomizer) {
        return WebClients.emptyOn4xx(() -> Optional.ofNullable(openLibraryWebClient.get()
                .uri(builder -> {
                    builder.path(SEARCH_PATH)
                        .queryParam(LIMIT_PARAM, limit)
                        .queryParam(FIELDS_PARAM, SEARCH_FIELDS);
                    queryCustomizer.accept(builder);
                    return builder.build();
                })
                .retrieve()
                .bodyToMono(OpenLibrarySearchResponse.class)
                .block())
            .map(OpenLibrarySearchResponse::docs), LOG, "OpenLibrary search")
            .orElse(List.of());
    }

    private Optional<OpenLibraryWork> fetchWork(final String workKey) {
        return WebClients.emptyOn4xx(() -> Optional.ofNullable(openLibraryWebClient.get()
                .uri(WORK_PATH, workKey)
                .retrieve()
                .bodyToMono(OpenLibraryWork.class)
                .block()), LOG, "OpenLibrary work fetch workKey=" + workKey);
    }

    private static boolean titleMatches(final OpenLibrarySearchDoc doc, final String queryTitle) {
        final String docTitle = doc.title();
        return docTitle != null && TextMatch.titleWithinQuery(docTitle, queryTitle);
    }
}
