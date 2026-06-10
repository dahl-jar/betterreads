package com.betterreads.catalog.staging;

import java.util.concurrent.Executor;

import com.betterreads.common.scheduling.JobExecutors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the single-thread executor the promotion poll runs on, off the shared scheduler thread.
 */
@Configuration
public class PromotionPollConfig {

    @Bean
    Executor promotionPollExecutor() {
        return JobExecutors.singleThread("promotion-poll-");
    }
}
