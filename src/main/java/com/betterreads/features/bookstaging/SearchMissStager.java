package com.betterreads.features.bookstaging;

import com.betterreads.bookdiscovery.BookDiscovery;
import com.betterreads.logging.LogSanitizer;
import com.betterreads.searchmiss.SearchMissSink;
import com.betterreads.searchmiss.SearchQueryKey;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Stages books for a search with no local hit, off the request thread.
 *
 * <p>Staging calls external sources, so it runs on a bounded executor and the request returns the
 * empty result right away. A query staged once is held for the dedup window, so repeats of the same
 * miss reach the external sources once.
 */
class SearchMissStager implements SearchMissSink {

    private static final Logger LOG = LoggerFactory.getLogger(SearchMissStager.class);

    private static final long MAX_TRACKED_QUERIES = 10_000L;

    private static final String UNKNOWN_SOURCE = "an unknown source";

    private final BookDiscovery bookDiscovery;

    private final Executor executor;

    private final Cache<String, Boolean> recentlyStaged;

    public SearchMissStager(
        final BookDiscovery bookDiscovery,
        final Executor executor,
        final Duration dedupWindow
    ) {
        this.bookDiscovery = bookDiscovery;
        this.executor = executor;
        this.recentlyStaged = Caffeine.newBuilder()
            .expireAfterWrite(dedupWindow)
            .maximumSize(MAX_TRACKED_QUERIES)
            .build();
    }

    @Override
    public void stage(final String query) {
        final String key = SearchQueryKey.of(query);
        if (recentlyStaged.asMap().putIfAbsent(key, Boolean.TRUE) != null) {
            return;
        }
        try {
            executor.execute(() -> runStaging(query));
        } catch (RejectedExecutionException ex) {
            recentlyStaged.invalidate(key);
        }
    }

    private void runStaging(final String query) {
        try {
            bookDiscovery.searchAndStage(query);
        } catch (WebClientResponseException ex) {
            LOG.warn("catalog.search-miss staging got {} from {}",
                ex.getStatusCode().value(), LogSanitizer.forLog(sourceHost(ex)));
        } catch (WebClientException | DataAccessException ex) {
            LOG.warn("catalog.search-miss staging failed ({})", ex.getClass().getSimpleName());
        }
    }

    private static String sourceHost(final WebClientResponseException ex) {
        return Optional.ofNullable(ex.getRequest())
            .map(request -> request.getURI().getHost())
            .orElse(UNKNOWN_SOURCE);
    }
}
