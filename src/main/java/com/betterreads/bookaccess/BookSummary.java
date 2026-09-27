package com.betterreads.bookaccess;

import java.math.BigDecimal;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * A book as shown on a shelf entry or a reader's review list.
 *
 * @param dedupKey the public lookup key
 * @param authors sorted by name
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
