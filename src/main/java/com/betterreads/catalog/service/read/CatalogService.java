package com.betterreads.catalog.service.read;

import com.betterreads.catalog.service.source.SourceBook;

import com.betterreads.catalog.entity.Book;

/** Catalog persistence operations. */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
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
     * <p>Not race-safe across replicas: two concurrent upserts of the same source key can both
     * miss on the lookup and insert duplicate rows.
     */
    Book upsertFromSource(SourceBook source);
}
