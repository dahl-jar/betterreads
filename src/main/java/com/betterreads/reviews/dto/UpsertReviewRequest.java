package com.betterreads.reviews.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

/**
 * Body of {@code PUT /api/v1/books/{key}/reviews/me}. A request with only a rating is a rating-only
 * review.
 *
 * @param rating the 1-5 star rating
 * @param title the review headline, null or empty for a rating-only review
 * @param body the review text, null or empty for a rating-only review
 */
public record UpsertReviewRequest(
    @NotNull @Min(1) @Max(5) Integer rating,
    @Size(max = 255) @Nullable String title,
    @Size(max = 5000) @Nullable String body
) {
}
