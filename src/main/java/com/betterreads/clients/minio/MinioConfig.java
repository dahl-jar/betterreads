package com.betterreads.clients.minio;

import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class MinioConfig {

    private final MinioProperties properties;

    MinioConfig(final MinioProperties properties) {
        this.properties = properties;
    }

    @Bean
    MinioClient minioClient() {
        return MinioClient.builder()
            .endpoint(properties.endpoint())
            .credentials(properties.accessKey(), properties.secretKey())
            .build();
    }
}
