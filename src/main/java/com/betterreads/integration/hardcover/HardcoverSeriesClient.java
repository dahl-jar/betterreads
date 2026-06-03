package com.betterreads.integration.hardcover;

import com.betterreads.catalog.service.SourceSeries;
import java.util.Optional;

/**
 * Resolves a series and its ordered volumes from Hardcover.
 *
 * <p>A DI seam with one implementation today, not a lambda target.
 */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface HardcoverSeriesClient {

    /**
     * Returns the series matching the query, or empty if none matches.
     *
     * <p>Resolution is two calls: a Series search picks the candidate with the most readers, then an
     * enumeration lists its volumes. The volumes come back collapsed to one per position, English
     * editions only, capped at the series' primary book count.
     */
    Optional<SourceSeries> fetchSeries(String query);
}
