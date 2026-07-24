package com.betterreads.reviews.dto;

import java.math.BigDecimal;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * The reader-community rating for a book: the average, the number of ratings, and the per-star
 * breakdown.
 *
 * <p>Built from BetterReads reviews alone. The source rating from external catalogs is on the book
 * detail.
 *
 * @param average the mean reader rating, null when the book has no ratings
 * @param count the number of reader ratings
 * @param distribution the count at each star, 5 down to 1, every star present
 */
public record CommunityRatingResponse(
    @Nullable BigDecimal average,
    long count,
    List<StarCount> distribution
) {

    public CommunityRatingResponse {
        distribution = List.copyOf(distribution);
    }

    @Override
    public List<StarCount> distribution() {
        return List.copyOf(distribution);
    }
}
