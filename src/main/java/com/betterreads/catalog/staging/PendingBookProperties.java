package com.betterreads.catalog.staging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Staging configuration bound from {@code betterreads.catalog.staging.*}.
 *
 * @param pollEnabled whether the scheduled promotion poll runs automatically
 */
@ConfigurationProperties(prefix = "betterreads.catalog.staging")
public record PendingBookProperties(boolean pollEnabled) {
}
