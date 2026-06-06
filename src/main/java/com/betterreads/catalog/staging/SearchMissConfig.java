package com.betterreads.catalog.staging;

import com.betterreads.catalog.service.pipeline.CatalogSearchService;
import com.betterreads.catalog.service.pipeline.SearchMissStager;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Wires the off-thread executor and the {@link SearchMissStager} that resolves a search query's
 * series and author into the catalog.
 *
 * <p>The pool caps at {@code MAX_POOL} threads and the queue at {@code QUEUE_CAPACITY}; once both are
 * full the reject policy discards further work, so a flood of queries stays within fixed thread and
 * memory limits. The 24-hour dedup window keeps every query resolving at most once a day, so staging
 * on every search rather than only on a miss does not multiply external calls.
 */
@Configuration
public class SearchMissConfig {

    private static final int CORE_POOL = 2;

    private static final int MAX_POOL = 4;

    private static final int QUEUE_CAPACITY = 50;

    private static final int SOURCE_FETCH_CORE_POOL = 3;

    private static final int SOURCE_FETCH_MAX_POOL = 6;

    private static final int SOURCE_FETCH_QUEUE_CAPACITY = 100;

    private static final Duration DEDUP_WINDOW = Duration.ofHours(24);

    /** Bounded executor that runs the staging fan-out for search misses. */
    // PMD.DoNotUseThreads: Spring manages this bounded executor's lifecycle and thread pool.
    @SuppressWarnings("PMD.DoNotUseThreads")
    @Bean
    Executor searchMissExecutor() {
        final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL);
        executor.setMaxPoolSize(MAX_POOL);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix("search-miss-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }

    /** Stager that fans a missed query out to the catalog sources off the request thread. */
    @Bean
    SearchMissStager searchMissStager(
        final CatalogSearchService catalogSearch,
        final Executor searchMissExecutor
    ) {
        return new SearchMissStager(catalogSearch, searchMissExecutor, DEDUP_WINDOW);
    }

    /** Bounded executor that fetches a candidate's sources concurrently within a wave. */
    @Bean
    Executor sourceFetchExecutor() {
        final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(SOURCE_FETCH_CORE_POOL);
        executor.setMaxPoolSize(SOURCE_FETCH_MAX_POOL);
        executor.setQueueCapacity(SOURCE_FETCH_QUEUE_CAPACITY);
        executor.setThreadNamePrefix("source-fetch-");
        executor.initialize();
        return executor;
    }
}
