package com.betterreads.catalog.refresh;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Catalog-refresh configuration bound from {@code betterreads.catalog.refresh.*}.
 *
 * @param enabled whether the scheduled daily author and series re-resolve runs
 */
@ConfigurationProperties(prefix = "betterreads.catalog.refresh")
public record CatalogRefreshProperties(boolean enabled) {
}
