package com.betterreads.features.catalogrefresh;

import java.util.concurrent.Executor;

import com.betterreads.scheduling.JobExecutors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Executor for the catalog refresh, so its hours-long run stays off the shared scheduler thread. */
@Configuration
class CatalogRefreshConfig {

    @Bean
    Executor catalogRefreshExecutor() {
        return JobExecutors.singleThread("catalog-refresh-");
    }
}
