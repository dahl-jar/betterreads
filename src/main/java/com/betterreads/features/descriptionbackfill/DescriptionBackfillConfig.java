package com.betterreads.features.descriptionbackfill;

import java.util.concurrent.Executor;

import com.betterreads.scheduling.JobExecutors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Executor for the description backfill, so its iTunes calls stay off the shared scheduler thread. */
@Configuration
class DescriptionBackfillConfig {

    @Bean
    Executor descriptionBackfillExecutor() {
        return JobExecutors.singleThread("description-backfill-");
    }
}
