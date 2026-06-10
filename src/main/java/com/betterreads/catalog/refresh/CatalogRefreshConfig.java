package com.betterreads.catalog.refresh;

import java.util.concurrent.Executor;

import com.betterreads.common.scheduling.JobExecutors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the single-thread executor the catalog refresh runs on, off the shared scheduler thread.
 */
@Configuration
public class CatalogRefreshConfig {

    @Bean
    Executor catalogRefreshExecutor() {
        return JobExecutors.singleThread("catalog-refresh-");
    }
}
