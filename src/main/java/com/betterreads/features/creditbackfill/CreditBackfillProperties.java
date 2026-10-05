package com.betterreads.features.creditbackfill;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "betterreads.catalog.credit-backfill")
record CreditBackfillProperties(boolean enabled, int sliceSize, Duration pause) {
}
