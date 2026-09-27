package com.betterreads.features.bookstaging;

import java.util.concurrent.Executor;

import com.betterreads.scheduling.JobExecutors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Single-thread executor that keeps the promotion poll off the shared scheduler thread. */
@Configuration
class PromotionPollConfig {

    @Bean
    Executor promotionPollExecutor() {
        return JobExecutors.singleThread("promotion-poll-");
    }
}
