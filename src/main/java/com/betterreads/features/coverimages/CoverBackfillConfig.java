package com.betterreads.features.coverimages;

import java.util.concurrent.Executor;

import com.betterreads.scheduling.JobExecutors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Single-thread executor that keeps the cover backfill off the shared scheduler thread. */
@Configuration
class CoverBackfillConfig {

    @Bean
    Executor coverBackfillExecutor() {
        return JobExecutors.singleThread("cover-backfill-");
    }
}
