package com.betterreads.catalog.service.read;

import com.betterreads.catalog.service.source.MergedBook;
import com.betterreads.catalog.service.source.SourceBook;

import com.betterreads.catalog.entity.Book;

/** Catalog persistence operations. */
public interface CatalogService {

    /**
     * Upserts a {@link SourceBook} into the local catalog, taking its series as authoritative.
     *
     * <p>Identity is resolved per source: a Google Books {@code SourceBook} matches an existing
     * row by {@code googleBooksVolumeId}, an OpenLibrary one by {@code openLibraryWorkKey}.
     * When no row matches, a new {@link Book} is inserted along with any missing authors and
     * the author-join row. When a row matches, populated fields on the source overwrite the
     * stored values; null source fields leave the stored value alone. The series name and position
     * overwrite the stored values, including clearing them.
     *
     * <p>Not race-safe across replicas: two concurrent upserts of the same source key can both
     * miss on the lookup and insert duplicate rows.
     */
    Book upsertFromSource(SourceBook source);

    /**
     * Upserts a {@link MergedBook}, clearing a stored series only when Hardcover resolved.
     *
     * <p>The series is cleared when the merged book carries none and Hardcover resolved on the
     * collect. When Hardcover did not resolve, the stored series is kept.
     */
    Book upsertFromSource(MergedBook merged);
}
