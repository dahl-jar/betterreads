package com.betterreads.features.metadatacheck;

import java.util.concurrent.Executor;

import com.betterreads.scheduling.JobExecutors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class MetadataCheckConfig {

    @Bean
    Executor metadataCheckExecutor() {
        return JobExecutors.singleThread("metadata-check-");
    }
}
