package com.betterreads.catalog.service.pipeline;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.web.reactive.function.client.WebClientException;

/**
 * Stages a candidate for a search that returned no local hit, off the request thread.
 *
 * <p>Staging fans out to external sources, so it runs on a bounded executor and the request returns
 * the empty result right away. A normalized query staged once is held for the dedup window, so a
 * repeat of the same miss returns immediately and the external sources are hit once per query.
 */
public class SearchMissStager {

    private static final Logger LOG = LoggerFactory.getLogger(SearchMissStager.class);

    private static final long MAX_TRACKED_QUERIES = 10_000L;

    private final CatalogSearchService catalogSearch;

    private final Executor executor;

    private final Cache<String, Boolean> recentlyStaged;

    public SearchMissStager(
        final CatalogSearchService catalogSearch,
        final Executor executor,
        final Duration dedupWindow
    ) {
        this.catalogSearch = catalogSearch;
        this.executor = executor;
        this.recentlyStaged = Caffeine.newBuilder()
            .expireAfterWrite(dedupWindow)
            .maximumSize(MAX_TRACKED_QUERIES)
            .build();
    }

    /** Stages the query off-thread once per dedup window, dropping repeats within the window. */
    public void stage(final String query) {
        final String key = normalize(query);
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
            catalogSearch.searchAndStage(query);
        } catch (WebClientException | DataAccessException ex) {
            LOG.warn("catalog.search-miss staging failed ({})", ex.getClass().getSimpleName());
        }
    }

    private static String normalize(final String query) {
        return query.strip().toLowerCase(Locale.ROOT);
    }
}
