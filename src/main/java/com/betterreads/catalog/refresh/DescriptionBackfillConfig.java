package com.betterreads.catalog.refresh;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Wires the single-thread executor the description backfill runs on, off the shared scheduler thread.
 */
@Configuration
public class DescriptionBackfillConfig {

    private static final int POOL_SIZE = 1;

    private static final int QUEUE_CAPACITY = 1;

    /** Single-thread executor that runs one backfill at a time, off the scheduler thread. */
    // PMD.DoNotUseThreads: Spring manages this bounded executor's lifecycle and thread pool.
    @SuppressWarnings("PMD.DoNotUseThreads")
    @Bean
    Executor descriptionBackfillExecutor() {
        final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(POOL_SIZE);
        executor.setMaxPoolSize(POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix("description-backfill-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
