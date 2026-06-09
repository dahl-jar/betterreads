package com.betterreads.catalog.refresh;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Description-backfill configuration bound from {@code betterreads.catalog.description-backfill.*}.
 *
 * @param enabled whether the scheduled description backfill runs
 * @param fullSweep whether the scheduled run sweeps every book once instead of the thin-only slice
 */
@ConfigurationProperties(prefix = "betterreads.catalog.description-backfill")
public record DescriptionBackfillProperties(boolean enabled, boolean fullSweep) {
}
