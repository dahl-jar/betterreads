package com.betterreads.features.descriptionbackfill;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param fullSweep re-check every keyed book in one run, the default run takes a slice of thin descriptions
 */
@ConfigurationProperties(prefix = "betterreads.catalog.description-backfill")
record DescriptionBackfillProperties(boolean enabled, boolean fullSweep) {
}
