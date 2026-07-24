package com.betterreads.common.scheduling;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Builds the single-thread executors that long scheduled jobs run on, off the shared scheduler
 * thread.
 */
public final class JobExecutors {

    private static final int POOL_SIZE = 1;

    private static final int QUEUE_CAPACITY = 1;

    private JobExecutors() {
    }

    /**
     * Returns a single-thread executor that runs one job at a time. The one-deep queue and abort
     * policy reject a second submission made while a run is in progress.
     */
    // PMD.DoNotUseThreads: Spring manages this bounded executor's lifecycle and thread pool.
    @SuppressWarnings("PMD.DoNotUseThreads")
    public static Executor singleThread(final String threadNamePrefix) {
        final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(POOL_SIZE);
        executor.setMaxPoolSize(POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
