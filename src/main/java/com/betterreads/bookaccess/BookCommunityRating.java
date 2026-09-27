package com.betterreads.bookaccess;

import java.math.BigDecimal;

import org.jspecify.annotations.Nullable;

/**
 * A book's stored community rating aggregate.
 *
 * @param average null when no rated review remains
 */
public record BookCommunityRating(
    long bookId,
    @Nullable BigDecimal average,
    int count
) {
}
