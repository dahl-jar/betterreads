package com.betterreads.features.bookstaging;

import com.betterreads.bookdiscovery.BookDiscovery;
import com.betterreads.logging.LogSanitizer;
import com.betterreads.searchmiss.SearchMissSink;
import com.betterreads.searchmiss.SearchQueryKey;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

class SearchMissStager implements SearchMissSink {

    private static final Logger LOG = LoggerFactory.getLogger(SearchMissStager.class);

    private static final long MAX_TRACKED_QUERIES = 10_000L;

    private static final String UNKNOWN_SOURCE = "an unknown source";

    private static final String STAGING_FAILED = "catalog.search-miss staging failed ({})";

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
            CompletableFuture.runAsync(() -> bookDiscovery.searchAndStage(query), executor)
                .whenComplete((ignored, failure) -> onRunEnd(key, failure));
        } catch (RejectedExecutionException ex) {
            recentlyStaged.invalidate(key);
        }
    }

    private void onRunEnd(final String key, final @Nullable Throwable failure) {
        if (failure != null) {
            recentlyStaged.invalidate(key);
            logFailure(unwrap(failure));
        }
    }

    private static Throwable unwrap(final Throwable failure) {
        final Throwable cause = failure.getCause();
        return failure instanceof CompletionException && cause != null ? cause : failure;
    }

    private static void logFailure(final Throwable cause) {
        final String type = LogSanitizer.forLog(cause.getClass().getSimpleName());
        switch (cause) {
            case WebClientResponseException ex -> LOG.warn("catalog.search-miss staging got {} from {}",
                ex.getStatusCode().value(), LogSanitizer.forLog(sourceHost(ex)));
            case WebClientRequestException ex -> LOG.warn("catalog.search-miss staging failed ({}) from {}",
                type, LogSanitizer.forLog(sourceHost(ex)));
            case DataAccessException ex -> LOG.warn(STAGING_FAILED, type);
            default -> LOG.atWarn().setCause(cause).log(STAGING_FAILED, type);
        }
    }

    private static String sourceHost(final WebClientResponseException ex) {
        return Optional.ofNullable(ex.getRequest())
            .map(request -> request.getURI().getHost())
            .orElse(UNKNOWN_SOURCE);
    }

    private static String sourceHost(final WebClientRequestException ex) {
        return Optional.ofNullable(ex.getUri().getHost()).orElse(UNKNOWN_SOURCE);
    }
}
