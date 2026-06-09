package com.betterreads.integration.itunes;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;

import com.betterreads.common.ratelimit.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * Apple Books search over the iTunes Search API.
 *
 * <p>A shared rate limiter paces calls under the unauthenticated cap across every replica: when the
 * budget empties a call waits up to {@link #MAX_TOKEN_WAIT} for a permit and skips the source when
 * none frees, so a backfill drains then continues at the refill rate without holding a thread for a
 * full window. A 4xx resolves to empty; 5xx and network failures propagate.
 */
@Component
public class ItunesApi {

    private static final Logger LOG = LoggerFactory.getLogger(ItunesApi.class);

    private static final JsonMapper JSON = new JsonMapper();

    private static final String SEARCH_PATH = "/search";

    private static final String EBOOK_MEDIA = "ebook";

    private static final int SEARCH_LIMIT = 1;

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

    /** Returns the first result's title and description for the search term, or empty when none. */
    public Optional<ItunesResult> firstResult(final String term) {
        return search(term)
            .map(body -> JSON.readTree(body).path("results").path(0))
            .map(node -> new ItunesResult(
                node.path("trackName").asString(""), node.path("description").asString("")))
            .filter(result -> !result.description().isBlank());
    }

    private Optional<String> search(final String term) {
        if (!acquireToken()) {
            LOG.debug("itunes.search skipped: rate budget exhausted, falling through");
            return Optional.empty();
        }
        return get(builder -> builder
            .path(SEARCH_PATH)
            .queryParam("term", term)
            .queryParam("media", EBOOK_MEDIA)
            .queryParam("limit", SEARCH_LIMIT)
            .build());
    }

    /**
     * Takes a rate-limit permit, waiting up to {@link #MAX_TOKEN_WAIT}, returning false when none
     * frees in time. The bounded wait keeps a drained budget from holding an enrichment thread for a
     * full refill window; the description is optional and the backfill retries the book later.
     */
    private boolean acquireToken() {
        return rateLimiter.tryAcquire(MAX_TOKEN_WAIT);
    }

    private Optional<String> get(final Function<UriBuilder, URI> uri) {
        try {
            return Optional.ofNullable(itunesWebClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(String.class)
                .block());
        } catch (WebClientResponseException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                LOG.debug("itunes.get returned 4xx status={}", exception.getStatusCode().value());
                return Optional.empty();
            }
            throw exception;
        }
    }
}
