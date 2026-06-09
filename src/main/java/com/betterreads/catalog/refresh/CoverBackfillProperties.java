package com.betterreads.catalog.refresh;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cover-backfill configuration bound from {@code betterreads.catalog.cover-backfill.*}.
 *
 * @param enabled whether the scheduled cover backfill runs
 * @param fullSweep whether the scheduled run mirrors every un-mirrored book instead of one slice
 */
@ConfigurationProperties(prefix = "betterreads.catalog.cover-backfill")
public record CoverBackfillProperties(boolean enabled, boolean fullSweep) {
}
