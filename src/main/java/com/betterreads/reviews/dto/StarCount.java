package com.betterreads.reviews.dto;

/**
 * The number of reader ratings at one star value.
 *
 * @param star the rating, 1 to 5
 * @param count the number of reader ratings at that star
 */
public record StarCount(int star, long count) {
}
