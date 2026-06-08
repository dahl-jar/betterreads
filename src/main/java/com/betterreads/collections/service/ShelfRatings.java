package com.betterreads.collections.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.betterreads.reviews.entity.Review;
import com.betterreads.reviews.repository.ReviewRepository;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** Reads the caller's own star rating per book, to surface on a shelf listing. */
@Component
public class ShelfRatings {

    private final ReviewRepository reviews;

    public ShelfRatings(final ReviewRepository reviews) {
        this.reviews = reviews;
    }

    /** Returns the user's rating for the book, or null when they have not rated it. */
    public @Nullable Integer forBook(final Long userId, final Long bookId) {
        return reviews.findByUserIdAndBookId(userId, bookId)
            .map(Review::getRating)
            .orElse(null);
    }

    /** Returns the user's rating per book among the given books, omitting books they have not rated. */
    public Map<Long, Integer> forBooks(final Long userId, final List<Long> bookIds) {
        if (bookIds.isEmpty()) {
            return Map.of();
        }
        return reviews.findRatedByUserForBooks(userId, bookIds).stream()
            .collect(Collectors.toMap(Review::getBookId, Review::getRating));
    }
}
