package com.betterreads.integration.minio;

import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the {@link MinioClient} for the in-cluster MinIO from {@link MinioProperties}.
 */
@Configuration
public class MinioConfig {

    private final MinioProperties properties;

    public MinioConfig(final MinioProperties properties) {
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
