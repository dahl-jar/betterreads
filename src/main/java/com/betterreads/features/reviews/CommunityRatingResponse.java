package com.betterreads.features.reviews;

import java.math.BigDecimal;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Rating built from BetterReads reviews only.
 *
 * @param average null when the book has no ratings
 * @param distribution one entry per star, 5 down to 1, zero when nobody picked that star
 */
record CommunityRatingResponse(
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
