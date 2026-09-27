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
import tools.jackson.databind.json.JsonMapper;

/**
 * Apple Books search over the iTunes Search API.
 *
 * <p>A call waits up to {@code MAX_TOKEN_WAIT} for a rate permit and then skips the source, so a
 * drained budget does not park a backfill thread for a whole refill window. A 4xx resolves to
 * empty. 5xx and network failures propagate.
 */
@Component
public class ItunesApi {

    private static final Logger LOG = LoggerFactory.getLogger(ItunesApi.class);

    private static final JsonMapper JSON = new JsonMapper();

    private static final String SEARCH_PATH = "/search";

    private static final String EBOOK_MEDIA = "ebook";

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
            .flatMap(body -> JSON.readTree(body).path("results").valueStream())
            .map(node -> new ItunesResult(
                node.path("trackName").asString(""), node.path("description").asString("")))
            .toList();
    }

    private Optional<String> searchBody(final String term) {
        if (!rateLimiter.tryAcquire(MAX_TOKEN_WAIT)) {
            LOG.debug("itunes.search skipped, rate budget exhausted");
            return Optional.empty();
        }
        return WebClients.getBodyOrEmptyOn4xx(itunesWebClient, builder -> builder
            .path(SEARCH_PATH)
            .queryParam("term", term)
            .queryParam("media", EBOOK_MEDIA)
            .queryParam("limit", SEARCH_LIMIT)
            .build(), LOG, "itunes.search");
    }
}
