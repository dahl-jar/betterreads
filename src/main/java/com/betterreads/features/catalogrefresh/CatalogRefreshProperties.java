package com.betterreads.features.catalogrefresh;

import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "betterreads.catalog.refresh")
record CatalogRefreshProperties(
    boolean authorsEnabled,
    boolean seriesEnabled,
    @Positive int maxBooksPerRun
) {
}
