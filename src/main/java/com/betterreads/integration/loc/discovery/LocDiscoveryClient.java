package com.betterreads.integration.loc.discovery;

import java.time.LocalDate;
import java.util.List;

/**
 * Surfaces newly-cataloged books from the Library of Congress SRU endpoint for the discovery cron.
 *
 * <p>Separate from {@code BookSourceClient}: enrichment clients fetch one known book, the discovery
 * client walks a date-and-subject bucket for unknown new releases.
 */
// PMD.ImplicitFunctionalInterface: a Spring service abstraction the cron depends on, not a lambda target.
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface LocDiscoveryClient {

    /**
     * Returns the books in the given publication year and subject bucket cataloged on or after the
     * cutoff.
     *
     * <p>LoC has no server-side date-range query, so the year and subject narrow the bucket and the
     * cutoff is applied to each record's MARC 008 cataloging date in process. A record missing its
     * LCCN, title, or cataloging date is dropped.
     *
     * @param year publication year ({@code dc.date})
     * @param lcshSubject LCSH subject heading, e.g. {@code science fiction}
     * @param catalogedSince keep only records cataloged on or after this date
     */
    List<LocDiscoveryRecord> discoverByDate(int year, String lcshSubject, LocalDate catalogedSince);
}
