package com.betterreads.catalog.refresh;

import java.util.concurrent.Executor;

import com.betterreads.common.scheduling.JobExecutors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the single-thread executor the description backfill runs on, off the shared scheduler thread.
 */
@Configuration
public class DescriptionBackfillConfig {

    @Bean
    Executor descriptionBackfillExecutor() {
        return JobExecutors.singleThread("description-backfill-");
    }
}
