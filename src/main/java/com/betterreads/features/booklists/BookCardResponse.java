package com.betterreads.features.booklists;

import java.math.BigDecimal;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * A homepage list card. The rating is the source rating from external catalogs.
 *
 * @param key a source identifier shared with search and detail
 * @param authors sorted by name
 */
record BookCardResponse(
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
