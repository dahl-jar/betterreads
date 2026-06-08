package com.betterreads.reviews.repository;

/**
 * The number of reviews at one star rating for a book, returned by
 * {@link ReviewRepository#countByStarForBook}.
 *
 * @param star the rating, 1 to 5
 * @param count the number of reviews at that rating
 */
public record RatingBucket(int star, long count) {
}
