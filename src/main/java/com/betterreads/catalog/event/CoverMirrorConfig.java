package com.betterreads.catalog.event;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Wires the bounded executor that mirrors a promoted book's cover off the commit thread.
 */
@Configuration
public class CoverMirrorConfig {

    private static final int CORE_POOL = 2;

    private static final int MAX_POOL = 4;

    private static final int QUEUE_CAPACITY = 100;

    /** Bounded executor for promotion-time cover mirroring; excess work is dropped under load. */
    // PMD.DoNotUseThreads: Spring manages this bounded executor's lifecycle and thread pool.
    @SuppressWarnings("PMD.DoNotUseThreads")
    @Bean
    Executor coverMirrorExecutor() {
        final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL);
        executor.setMaxPoolSize(MAX_POOL);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix("cover-mirror-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.initialize();
        return executor;
    }
}
