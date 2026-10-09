package com.betterreads.features.coverimages;

import java.time.Duration;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "betterreads.catalog.cover-upgrade")
record CoverUpgradeProperties(boolean enabled, @Positive int batchSize, @NotNull Duration searchAgainAfter) {
}
