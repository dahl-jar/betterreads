package com.betterreads.catalog.refresh;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the single-thread executor the cover backfill runs on, off the shared scheduler thread.
 */
@Configuration
public class CoverBackfillConfig {

    @Bean
    Executor coverBackfillExecutor() {
        return BackfillExecutors.singleThread("cover-backfill-");
    }
}
