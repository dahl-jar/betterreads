package com.betterreads.features.reviews;

import org.jspecify.annotations.Nullable;

/** JPQL result row, average is null when the book has no rated reviews. */
public record ReviewRatingAggregate(@Nullable Double average, long count) {
}
