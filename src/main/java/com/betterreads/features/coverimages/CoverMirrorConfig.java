package com.betterreads.features.coverimages;

import com.betterreads.scheduling.JobExecutors;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Bounded executor that mirrors a promoted book's cover off the commit thread. */
@Configuration
class CoverMirrorConfig {

    private static final int CORE_POOL = 2;

    private static final int MAX_POOL = 4;

    private static final int QUEUE_CAPACITY = 100;

    /** Excess work is dropped under load. */
    // PMD.DoNotUseThreads: Spring manages this bounded executor's lifecycle and thread pool.
    @SuppressWarnings("PMD.DoNotUseThreads")
    @Bean
    Executor coverMirrorExecutor() {
        return JobExecutors.bounded(
            CORE_POOL, MAX_POOL, QUEUE_CAPACITY, "cover-mirror-", new ThreadPoolExecutor.DiscardPolicy());
    }
}
