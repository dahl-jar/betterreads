package com.betterreads.catalog.dto;

import java.math.BigDecimal;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * A book as shown on a shelf entry or a reader's review list, with the cover resolved to the served
 * URL.
 *
 * @param bookId the catalog id, which links the book to the reader's own shelf and review entries
 * @param dedupKey the public lookup key, shared with search and detail
 * @param title the book title
 * @param authors the author names, sorted
 * @param servedCoverUrl the cover URL the API serves, null when the book has no cover
 * @param averageRating the source average rating, null when no source supplied one
 */
public record BookSummary(
    long bookId,
    String dedupKey,
    String title,
    List<String> authors,
    @Nullable String servedCoverUrl,
    @Nullable BigDecimal averageRating
) {

    public BookSummary {
        authors = List.copyOf(authors);
    }

    @Override
    public List<String> authors() {
        return List.copyOf(authors);
    }
}
