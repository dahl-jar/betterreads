package com.betterreads.features.creditbackfill;

import java.util.concurrent.Executor;

import com.betterreads.scheduling.JobExecutors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class CreditBackfillConfig {

    @Bean
    Executor creditBackfillExecutor() {
        return JobExecutors.singleThread("credit-backfill-");
    }
}
