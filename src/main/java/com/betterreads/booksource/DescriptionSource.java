package com.betterreads.booksource;

import java.util.Optional;

/** Source that supplies only a description for a book the catalog already has. */
public interface DescriptionSource {

    BookFieldSource source();

    /** true when this source is asked only after every other source came up empty */
    default boolean fallbackOnly() {
        return false;
    }

    Optional<String> fetch(DescriptionLookup lookup);
}
