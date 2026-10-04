package com.betterreads.features.reviews;

import java.time.LocalDate;

import org.jspecify.annotations.Nullable;

/**
 * @param createdAt the day the review was first posted, edits leave it unchanged
 */
public record ReviewResponse(
    long id,
    String bookKey,
    @Nullable Integer rating,
    @Nullable String title,
    @Nullable String body,
    LocalDate createdAt,
    String author,
    long commentCount,
    @Nullable LocalDate readStartedAt,
    @Nullable LocalDate readFinishedAt
) {
}
