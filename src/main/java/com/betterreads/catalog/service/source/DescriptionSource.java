package com.betterreads.catalog.service.source;

import java.util.Optional;

/**
 * A source consulted for a book's description alone.
 *
 * <p>These sources fill the description field for a book the catalog already identifies, without
 * contributing a catalog record to the merge.
 */
public interface DescriptionSource {

    /** Returns the source identity. */
    BookFieldSource source();

    /** Returns true when the source competes only after every other source came up empty. */
    default boolean fallbackOnly() {
        return false;
    }

    /**
     * Returns a description for the book the lookup identifies, or empty when none is found.
     *
     * @param lookup the identifiers a description source can key on
     */
    Optional<String> fetch(DescriptionLookup lookup);
}
