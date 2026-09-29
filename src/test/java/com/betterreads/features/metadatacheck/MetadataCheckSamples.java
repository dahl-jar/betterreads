package com.betterreads.features.metadatacheck;

final class MetadataCheckSamples {

    static final int BATCH_SIZE = 10;

    static final int MAX_BOOKS = 100;

    static final int LOOKBACK_DAYS = 7;

    private MetadataCheckSamples() {
    }

    static MetadataCheckProperties properties(final boolean enabled) {
        return new MetadataCheckProperties(enabled, BATCH_SIZE, MAX_BOOKS, LOOKBACK_DAYS);
    }
}
