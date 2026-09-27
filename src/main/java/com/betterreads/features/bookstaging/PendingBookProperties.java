package com.betterreads.features.bookstaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "betterreads.catalog.staging")
record PendingBookProperties(boolean pollEnabled) {
}
