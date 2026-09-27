package com.betterreads.features.coverimages;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param fullSweep each run mirrors every un-mirrored book, one slice when false
 */
@ConfigurationProperties(prefix = "betterreads.catalog.cover-backfill")
record CoverBackfillProperties(boolean enabled, boolean fullSweep) {
}
