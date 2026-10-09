package com.betterreads.features.coverimages;

import java.util.concurrent.Executor;

import com.betterreads.scheduling.JobExecutors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class CoverUpgradeConfig {

    @Bean
    Executor coverUpgradeExecutor() {
        return JobExecutors.singleThread("cover-upgrade-");
    }
}
