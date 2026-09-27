package com.betterreads.features.reviews;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

/** A rating with no title or body is a rating-only review, blank text counts as none. */
record UpsertReviewRequest(
    @NotNull @Min(1) @Max(5) Integer rating,
    @Size(max = 255) @Nullable String title,
    @Size(max = 5000) @Nullable String body
) {
}
