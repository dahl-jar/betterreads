package com.betterreads.reviews.dto;

import java.time.LocalDate;

import org.jspecify.annotations.Nullable;

/**
 * A review returned by the review endpoints.
 *
 * @param id the review id
 * @param bookKey the book's public key, shared with search and detail
 * @param rating the 1-5 star rating
 * @param title the review headline, null for a rating-only review
 * @param body the review text, null for a rating-only review
 * @param createdAt the date the review was first posted
 */
public record ReviewResponse(
    long id,
    String bookKey,
    @Nullable Integer rating,
    @Nullable String title,
    @Nullable String body,
    LocalDate createdAt
) {
}
