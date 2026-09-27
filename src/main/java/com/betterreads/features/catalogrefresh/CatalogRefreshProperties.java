package com.betterreads.features.catalogrefresh;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "betterreads.catalog.refresh")
record CatalogRefreshProperties(boolean enabled) {
}
