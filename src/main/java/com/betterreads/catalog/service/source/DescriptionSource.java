package com.betterreads.catalog.service.source;

import java.util.Optional;

/**
 * A source consulted for a book's description alone.
 *
 * <p>Wikipedia and Apple Books carry descriptions but no catalog record worth merging, so they fill
 * the description field for a book the catalog already identifies.
 */
public interface DescriptionSource {

    /** Returns the source identity. */
    BookFieldSource source();

    /**
     * Returns a description for the book the lookup identifies, or empty when none is found.
     *
     * @param lookup the identifiers a description source can key on
     */
    Optional<String> fetch(DescriptionLookup lookup);
}
