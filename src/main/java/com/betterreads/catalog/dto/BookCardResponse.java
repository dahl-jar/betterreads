package com.betterreads.catalog.dto;

import java.math.BigDecimal;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * A book as shown on a homepage list card, with the source rating from external catalogs.
 *
 * @param key the lookup key, a source identifier shared with search and detail
 * @param title the book title
 * @param authors the author names, sorted
 * @param coverUrl the cover image url, null when none is known
 * @param firstPublishYear the first publication year, null when unknown
 * @param averageRating the source average rating, null when unrated
 * @param ratingCount the source rating count, null when unrated
 */
public record BookCardResponse(
    String key,
    String title,
    List<String> authors,
    @Nullable String coverUrl,
    @Nullable Integer firstPublishYear,
    @Nullable BigDecimal averageRating,
    @Nullable Integer ratingCount
) {

    public BookCardResponse {
        authors = List.copyOf(authors);
    }

    @Override
    public List<String> authors() {
        return List.copyOf(authors);
    }
}
