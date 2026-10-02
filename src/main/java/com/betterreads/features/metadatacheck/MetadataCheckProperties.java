package com.betterreads.features.metadatacheck;

import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "betterreads.catalog.metadata-check")
record MetadataCheckProperties(
    boolean enabled,
    @Positive int batchSize,
    @Positive int maxBooksPerRun
) {
}
