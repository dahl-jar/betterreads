package com.betterreads.features.reviews;

import java.time.LocalDate;

import org.jspecify.annotations.Nullable;

public record RecentReviewResponse(
    long id,
    String author,
    @Nullable Integer rating,
    @Nullable String title,
    String body,
    LocalDate createdAt,
    ReviewedBook book,
    long commentCount,
    @Nullable LocalDate readStartedAt,
    @Nullable LocalDate readFinishedAt
) {
}
