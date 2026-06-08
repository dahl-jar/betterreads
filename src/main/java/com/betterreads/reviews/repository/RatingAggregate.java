package com.betterreads.reviews.repository;

import org.jspecify.annotations.Nullable;

/**
 * The rating average and count for a book, returned by {@link ReviewRepository#aggregateRatingForBook}.
 *
 * @param average the mean rating, null when the book has no rated reviews
 * @param count the number of rated reviews
 */
public record RatingAggregate(@Nullable Double average, long count) {
}
