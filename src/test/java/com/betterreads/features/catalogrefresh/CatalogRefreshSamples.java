package com.betterreads.features.catalogrefresh;

final class CatalogRefreshSamples {

    static final int MAX_BOOKS = 50;

    private CatalogRefreshSamples() {
    }

    static CatalogRefreshProperties properties(final boolean authorsEnabled, final boolean seriesEnabled) {
        return new CatalogRefreshProperties(authorsEnabled, seriesEnabled, MAX_BOOKS);
    }
}
