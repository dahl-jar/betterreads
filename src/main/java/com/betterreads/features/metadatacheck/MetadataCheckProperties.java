package com.betterreads.features.metadatacheck;

import java.time.Duration;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "betterreads.catalog.metadata-check")
record MetadataCheckProperties(
    boolean enabled,
    @Positive int batchSize,
    @Positive int maxBooksPerRun,
    @NotNull Duration retryAfterFailure,
    @NotNull Duration retryAfterUnconfirmed,
    @Positive int maxAttempts,
    @Positive int checkVersion
) {
}
