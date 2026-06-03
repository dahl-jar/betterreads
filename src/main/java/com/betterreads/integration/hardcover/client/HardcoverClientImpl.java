package com.betterreads.integration.hardcover.client;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import com.betterreads.catalog.service.BookFieldSource;
import com.betterreads.catalog.service.SourceBook;
import com.betterreads.common.util.LogSanitizer;
import com.betterreads.common.util.TextMatch;
import com.betterreads.integration.hardcover.HardcoverClient;
import com.betterreads.integration.hardcover.dto.GraphQlRequest;
import com.betterreads.integration.hardcover.dto.HardcoverDocument;
import com.betterreads.integration.hardcover.dto.TypesenseHits;
import com.betterreads.integration.hardcover.dto.TypesenseSearchResponse;
import com.betterreads.integration.hardcover.mapper.HardcoverMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Hardcover GraphQL client.
 *
 * <p>One search call resolves a book; the hit document already carries every field. Hardcover ranks
 * by relevance, not edition quality, so the canonical work is the hit with the most reads rather
 * than the first one, and {@link #titleMatches} rejects a pick that drifted off the query. A 401
 * means the token expired or was revoked and resolves to empty; other 4xx resolve to empty; 5xx and
 * network failures propagate.
 */
@Component
public class HardcoverClientImpl implements HardcoverClient {

    private static final Logger LOG = LoggerFactory.getLogger(HardcoverClientImpl.class);

    private static final String SEARCH_QUERY = """
        query Search($q: String!) {
          search(query: $q, query_type: "Book", per_page: 5, page: 1) { results }
        }
        """;

    private static final ParameterizedTypeReference<TypesenseSearchResponse<HardcoverDocument>>
        BOOK_HITS = new ParameterizedTypeReference<>() { };

    private static final Comparator<HardcoverDocument> BY_READ_COUNT =
        Comparator.comparingInt(HardcoverClientImpl::readCount);

    private final WebClient hardcoverWebClient;

    private final HardcoverMapper mapper;

    public HardcoverClientImpl(final WebClient hardcoverWebClient, final HardcoverMapper mapper) {
        this.hardcoverWebClient = hardcoverWebClient;
        this.mapper = mapper;
    }

    @Override
    public BookFieldSource source() {
        return BookFieldSource.HARDCOVER;
    }

    @Override
    public Optional<SourceBook> fetchByIsbn(final String isbn) {
        return searchAndMap(isbn, document -> true);
    }

    @Override
    public Optional<SourceBook> fetchByTitleAuthor(final String title, final String author) {
        return searchAndMap(title,
            document -> titleMatches(document, title) && authorMatches(document, author));
    }

    @Override
    public Optional<SourceBook> fetchByHardcoverId(final String hardcoverId) {
        return searchAndMap(hardcoverId, document -> hardcoverId.equals(document.id()));
    }

    private Optional<SourceBook> searchAndMap(
        final String query,
        final Predicate<HardcoverDocument> accept
    ) {
        return search(query).filter(accept).map(mapper::toSourceBook);
    }

    private Optional<HardcoverDocument> search(final String query) {
        try {
            final TypesenseSearchResponse<HardcoverDocument> response = hardcoverWebClient.post()
                .bodyValue(new GraphQlRequest(SEARCH_QUERY, Map.of("q", query)))
                .retrieve()
                .bodyToMono(BOOK_HITS)
                .block();
            return TypesenseHits.documents(response).stream()
                .filter(document -> document.title() != null)
                .max(BY_READ_COUNT);
        } catch (WebClientResponseException exception) {
            return recover(exception, query);
        }
    }

    private Optional<HardcoverDocument> recover(
        final WebClientResponseException exception,
        final String query
    ) {
        if (exception.getStatusCode() == HttpStatus.UNAUTHORIZED) {
            LOG.warn("hardcover.auth token rejected (401), expired or revoked, regenerate at "
                + "hardcover.app query={}", LogSanitizer.forLog(query));
            return Optional.empty();
        }
        if (exception.getStatusCode().is4xxClientError()) {
            LOG.debug("hardcover.search returned 4xx status={}", exception.getStatusCode().value());
            return Optional.empty();
        }
        throw exception;
    }

    private static int readCount(final HardcoverDocument document) {
        final Integer reads = document.usersReadCount();
        if (reads != null) {
            return reads;
        }
        final Integer ratings = document.ratingsCount();
        return ratings == null ? 0 : ratings;
    }

    /** Returns true if the picked title and the query each contain the other, ignoring case. */
    private static boolean titleMatches(final HardcoverDocument document, final String query) {
        final String title = document.title();
        if (title == null) {
            return false;
        }
        final String trimmedTitle = title.trim();
        final String trimmedQuery = query.trim();
        return TextMatch.containsIgnoreCase(trimmedTitle, trimmedQuery)
            || TextMatch.containsIgnoreCase(trimmedQuery, trimmedTitle);
    }

    /**
     * Returns true if one of the document's author names matches the query author.
     *
     * <p>Hardcover searches only by title, so a shared title can resolve to a different author's
     * book; this rejects the pick when no author name contains the query (or the reverse), which
     * tolerates the spelling variants Hardcover carries ({@code George R.R. Martin} vs
     * {@code George R. R. Martin}).
     */
    private static boolean authorMatches(final HardcoverDocument document, final String author) {
        final List<String> names = document.authorNames();
        if (names == null) {
            return false;
        }
        final String trimmedAuthor = author.trim();
        return names.stream().anyMatch(name -> name != null
            && (TextMatch.containsIgnoreCase(name, trimmedAuthor)
                || TextMatch.containsIgnoreCase(trimmedAuthor, name)));
    }
}
