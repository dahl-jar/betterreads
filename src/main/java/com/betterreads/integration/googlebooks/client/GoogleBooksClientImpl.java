package com.betterreads.integration.googlebooks.client;

import java.util.List;
import java.util.Optional;

import com.betterreads.catalog.service.BookFieldSource;
import com.betterreads.catalog.service.SourceBook;
import com.betterreads.common.util.LogSanitizer;
import com.betterreads.integration.googlebooks.GoogleBooksClient;
import com.betterreads.integration.googlebooks.dto.Volume;
import com.betterreads.integration.googlebooks.dto.VolumeSearchResponse;
import com.betterreads.integration.googlebooks.mapper.GoogleBooksMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Google Books REST API client.
 *
 * <p>4xx responses resolve to {@link Optional#empty()} so callers treat "no such volume" and
 * "search returned nothing" uniformly. 5xx responses and network failures propagate as
 * {@link WebClientResponseException} so an upstream pipeline can choose to retry or fall back.
 *
 * <p>Search ordering on the {@code /volumes} endpoint biases toward the most recent reprint
 * for a given title, so {@link #fetchByTitleAuthor} returns reprint-edition metadata
 * (publishedDate, publisher, ISBN) rather than first-edition metadata.
 */
@Component
public class GoogleBooksClientImpl implements GoogleBooksClient {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleBooksClientImpl.class);

    private static final String VOLUMES_PATH = "/volumes";

    private static final String VOLUME_BY_ID_PATH = "/volumes/{volumeId}";

    private final WebClient googleBooksWebClient;

    private final GoogleBooksMapper mapper;

    public GoogleBooksClientImpl(
        final WebClient googleBooksWebClient,
        final GoogleBooksMapper mapper
    ) {
        this.googleBooksWebClient = googleBooksWebClient;
        this.mapper = mapper;
    }

    @Override
    public BookFieldSource source() {
        return BookFieldSource.GOOGLE_BOOKS;
    }

    @Override
    public Optional<SourceBook> fetchByIsbn(final String isbn) {
        return search("isbn:" + escapeForQuery(isbn)).map(mapper::toSourceBook);
    }

    @Override
    public Optional<SourceBook> fetchByTitleAuthor(final String title, final String author) {
        final String query = "intitle:\"" + escapeForQuery(title) + "\""
            + " inauthor:\"" + escapeForQuery(author) + "\"";
        return search(query).map(mapper::toSourceBook);
    }

    @Override
    public Optional<SourceBook> fetchByVolumeId(final String volumeId) {
        try {
            final Volume volume = googleBooksWebClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path(VOLUME_BY_ID_PATH)
                    .build(volumeId))
                .retrieve()
                .bodyToMono(Volume.class)
                .block();
            return Optional.ofNullable(mapper.toSourceBook(volume));
        } catch (WebClientResponseException ex) {
            if (ex.getStatusCode().is4xxClientError()) {
                LOG.debug("Google Books returned 4xx for volume lookup volumeId={} status={}",
                    LogSanitizer.forLog(volumeId), ex.getStatusCode().value());
                return Optional.empty();
            }
            throw ex;
        }
    }

    private Optional<Volume> search(final String query) {
        try {
            final VolumeSearchResponse response = googleBooksWebClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path(VOLUMES_PATH)
                    .queryParam("q", query)
                    .queryParam("maxResults", 1)
                    .build())
                .retrieve()
                .bodyToMono(VolumeSearchResponse.class)
                .block();
            if (response == null) {
                return Optional.empty();
            }
            final List<Volume> items = response.items();
            if (items == null || items.isEmpty()) {
                return Optional.empty();
            }
            return Optional.ofNullable(items.get(0));
        } catch (WebClientResponseException ex) {
            if (ex.getStatusCode().is4xxClientError()) {
                LOG.debug("Google Books returned 4xx for search query={} status={}",
                    LogSanitizer.forLog(query), ex.getStatusCode().value());
                return Optional.empty();
            }
            throw ex;
        }
    }

    /**
     * Strips characters that would break out of a {@code field:"..."} query term.
     *
     * <p>Google Books' query DSL uses double-quotes to delimit phrase terms; a literal
     * double-quote inside a user-provided title or author closes the phrase early and lets
     * the remainder of the string be interpreted as additional query operators. Backslash is
     * also dropped because it is the only character that could re-open quoting in some
     * Lucene-flavored interpretations.
     */
    private static String escapeForQuery(final String input) {
        return input.replace("\\", "").replace("\"", "");
    }
}
