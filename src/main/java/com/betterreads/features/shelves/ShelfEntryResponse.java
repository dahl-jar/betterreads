package com.betterreads.features.shelves;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * A shelved book with its shelf state. averageRating is the source rating, myRating the reader's
 * own 1-5 rating.
 */
record ShelfEntryResponse(
    String key,
    String title,
    List<String> authors,
    @Nullable String coverUrl,
    ReadingStatus status,
    boolean favorite,
    @Nullable LocalDate startedAt,
    @Nullable LocalDate finishedAt,
    @Nullable String notes,
    LocalDate addedAt,
    @Nullable BigDecimal averageRating,
    @Nullable Integer myRating
) {

    public ShelfEntryResponse {
        authors = List.copyOf(authors);
    }

    @Override
    public List<String> authors() {
        return List.copyOf(authors);
    }
}
