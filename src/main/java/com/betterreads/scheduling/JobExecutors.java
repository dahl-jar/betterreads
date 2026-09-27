package com.betterreads.scheduling;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Builds the bounded executors that background work runs on, off the calling thread. */
public final class JobExecutors {

    private static final int POOL_SIZE = 1;

    private static final int QUEUE_CAPACITY = 1;

    private JobExecutors() {
    }

    /** One job runs and one more waits in the queue, any further submission is rejected. */
    // PMD.DoNotUseThreads: Spring manages this bounded executor's lifecycle and thread pool.
    @SuppressWarnings("PMD.DoNotUseThreads")
    public static Executor singleThread(final String threadNamePrefix) {
        return bounded(
            POOL_SIZE, POOL_SIZE, QUEUE_CAPACITY, threadNamePrefix, new ThreadPoolExecutor.AbortPolicy());
    }

    public static Executor bounded(
        final int corePoolSize,
        final int maxPoolSize,
        final int queueCapacity,
        final String threadNamePrefix,
        final RejectedExecutionHandler whenFull
    ) {
        final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setRejectedExecutionHandler(whenFull);
        executor.initialize();
        return executor;
    }
}
