package com.betterreads.clients.itunes;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import com.betterreads.clients.http.WebClients;
import com.betterreads.ratelimit.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Apple Books search and ISBN lookup over the iTunes Search API.
 *
 * <p>A call waits up to five seconds for a rate permit, so a drained budget does not block the caller
 * for a whole refill window. A search that gets no permit returns empty. A search 5xx or network
 * failure propagates.
 * An ISBN lookup throws ItunesUnavailableException on a missed permit, a 5xx, a network failure or an
 * unreadable reply. A 4xx resolves to empty.
 */
@Component
public class ItunesApi {

    private static final Logger LOG = LoggerFactory.getLogger(ItunesApi.class);

    private static final JsonMapper JSON = new JsonMapper();

    private static final String SEARCH_PATH = "/search";

    private static final String LOOKUP_PATH = "/lookup";

    private static final String RESULTS = "results";

    private static final String EBOOK = "ebook";

    private static final String THUMBNAIL_SIZE = "100x100bb";

    private static final String LARGEST_SIZE = "3000x3000bb";

    private static final int SEARCH_LIMIT = 3;

    private static final Duration MAX_TOKEN_WAIT = Duration.ofSeconds(5);

    private final WebClient itunesWebClient;

    private final RateLimiter rateLimiter;

    public ItunesApi(
        final WebClient itunesWebClient,
        @Qualifier("itunesRateLimiter") final RateLimiter rateLimiter
    ) {
        this.itunesWebClient = itunesWebClient;
        this.rateLimiter = rateLimiter;
    }

    public List<ItunesResult> search(final String term) {
        return searchBody(term).stream()
            .flatMap(body -> JSON.readTree(body).path(RESULTS).valueStream())
            .map(node -> new ItunesResult(
                node.path("trackName").asString(""), node.path("description").asString("")))
            .toList();
    }

    public Optional<ItunesBook> lookupByIsbn(final String isbn13) {
        if (!rateLimiter.tryAcquire(MAX_TOKEN_WAIT)) {
            throw new ItunesUnavailableException();
        }
        try {
            return WebClients.getBodyOrEmptyOn4xx(itunesWebClient, builder -> builder
                    .path(LOOKUP_PATH)
                    .queryParam("isbn", isbn13)
                    .build(), LOG, "itunes.lookup")
                .flatMap(body -> JSON.readTree(body).path(RESULTS).valueStream()
                    .filter(node -> EBOOK.equals(node.path("kind").asString("")))
                    .findFirst())
                .flatMap(node -> ItunesBook.of(
                    node.path("artworkUrl100").asString("").replace(THUMBNAIL_SIZE, LARGEST_SIZE),
                    node.path("trackViewUrl").asString("")));
        } catch (WebClientException | JacksonException ex) {
            throw new ItunesUnavailableException(ex);
        }
    }

    private Optional<String> searchBody(final String term) {
        if (!rateLimiter.tryAcquire(MAX_TOKEN_WAIT)) {
            LOG.debug("itunes.search skipped, rate budget exhausted");
            return Optional.empty();
        }
        return WebClients.getBodyOrEmptyOn4xx(itunesWebClient, builder -> builder
            .path(SEARCH_PATH)
            .queryParam("term", term)
            .queryParam("media", EBOOK)
            .queryParam("limit", SEARCH_LIMIT)
            .build(), LOG, "itunes.search");
    }
}
