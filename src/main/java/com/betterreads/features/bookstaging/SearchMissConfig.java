package com.betterreads.features.bookstaging;

import com.betterreads.bookdiscovery.BookDiscovery;
import com.betterreads.scheduling.JobExecutors;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bounded executors for source fetches and search-miss staging.
 *
 * <p>Pool and queue are both capped and the abort policy rejects work past that, so a flood of
 * queries stays within fixed thread and memory limits.
 */
@Configuration
class SearchMissConfig {

    private static final int SEARCH_MISS_CORE_POOL = 2;

    private static final int SEARCH_MISS_MAX_POOL = 4;

    private static final int SEARCH_MISS_QUEUE_CAPACITY = 50;

    private static final int SOURCE_FETCH_CORE_POOL = 3;

    private static final int SOURCE_FETCH_MAX_POOL = 6;

    private static final int SOURCE_FETCH_QUEUE_CAPACITY = 100;

    private static final Duration DEDUP_WINDOW = Duration.ofHours(24);

    @Bean
    Executor searchMissExecutor() {
        return boundedExecutor(
            SEARCH_MISS_CORE_POOL, SEARCH_MISS_MAX_POOL, SEARCH_MISS_QUEUE_CAPACITY, "search-miss-");
    }

    @Bean
    SearchMissStager searchMissStager(
        final BookDiscovery bookDiscovery,
        final Executor searchMissExecutor
    ) {
        return new SearchMissStager(bookDiscovery, searchMissExecutor, DEDUP_WINDOW);
    }

    @Bean
    Executor sourceFetchExecutor() {
        return boundedExecutor(
            SOURCE_FETCH_CORE_POOL, SOURCE_FETCH_MAX_POOL, SOURCE_FETCH_QUEUE_CAPACITY, "source-fetch-");
    }

    // PMD.DoNotUseThreads: Spring manages this bounded executor's lifecycle and thread pool.
    @SuppressWarnings("PMD.DoNotUseThreads")
    private static Executor boundedExecutor(
        final int core, final int max, final int queue, final String threadPrefix) {
        return JobExecutors.bounded(core, max, queue, threadPrefix, new ThreadPoolExecutor.AbortPolicy());
    }
}
