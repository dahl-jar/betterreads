package com.betterreads.catalog.service;

import com.betterreads.catalog.entity.Book;

/** Catalog persistence operations. */
public interface CatalogService {

    /**
     * Upserts a {@link SourceBook} into the local catalog.
     *
     * <p>Identity is resolved per source: a Google Books {@code SourceBook} matches an existing
     * row by {@code googleBooksVolumeId}, an OpenLibrary one by {@code openLibraryWorkKey}.
     * When no row matches, a new {@link Book} is inserted along with any missing authors and
     * the author-join row. When a row matches, populated fields on the source overwrite the
     * stored values; null source fields leave the stored value alone.
     *
     * <p>Not race-safe across replicas. The multi-source pipeline replaces this with native
     * {@code INSERT ... ON CONFLICT} once concurrent cold-reads become a real path.
     */
    Book upsertFromSource(SourceBook source);
}
