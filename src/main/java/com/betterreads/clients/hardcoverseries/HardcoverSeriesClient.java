package com.betterreads.clients.hardcoverseries;

import com.betterreads.booksource.SourceSeries;
import java.util.Optional;

/** Resolves a series and its ordered volumes from Hardcover. */
// PMD.ImplicitFunctionalInterface: a Spring service contract with a @Component impl that happens to have one method.
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface HardcoverSeriesClient {

    /**
     * Returns the series with one English book per position, up to its primary book count, or empty if
     * no series matches.
     */
    Optional<SourceSeries> fetchSeries(String query);
}
