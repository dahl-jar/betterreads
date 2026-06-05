package com.betterreads.integration.loc.discovery;

import java.time.LocalDate;

import org.jspecify.annotations.Nullable;

/**
 * A newly-cataloged book surfaced by the LoC discovery walk.
 *
 * <p>Carries only the fields the discovery cron needs to dedup and enqueue: the identity keys, a
 * title for logging, and the cataloging date parsed from MARC 008. Enrichment fills the full record
 * later by looking the book up by {@code lccn}.
 *
 * @param lccn Library of Congress Control Number, the dedup key
 * @param isbn13 ISBN-13, or null when the record has none
 * @param title the cataloged title
 * @param catalogedOn the MARC 008 cataloging-entry date
 */
public record LocDiscoveryRecord(
    String lccn,
    @Nullable String isbn13,
    String title,
    LocalDate catalogedOn
) { }
