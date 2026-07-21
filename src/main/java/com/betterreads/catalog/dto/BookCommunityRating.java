package com.betterreads.catalog.dto;

import java.math.BigDecimal;

import org.jspecify.annotations.Nullable;

/**
 * A book's stored community rating aggregate.
 *
 * @param bookId the local catalog id, for keying the per-book star counts
 * @param average the community average rating, null when no rated review remains
 * @param count the number of community ratings
 */
public record BookCommunityRating(
    long bookId,
    @Nullable BigDecimal average,
    int count
) {
}
