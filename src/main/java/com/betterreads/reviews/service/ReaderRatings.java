package com.betterreads.reviews.service;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.betterreads.reviews.entity.Review;
import com.betterreads.reviews.repository.ReviewRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads a reader's star ratings. */
@Service
public class ReaderRatings {

    private final ReviewRepository reviews;

    public ReaderRatings(final ReviewRepository reviews) {
        this.reviews = reviews;
    }

    /** Returns the reader's rating for the book, or empty when they have not rated it. */
    @Transactional(readOnly = true)
    public Optional<Integer> ratingOf(final long userId, final long bookId) {
        return reviews.findByUserIdAndBookId(userId, bookId).map(Review::getRating);
    }

    /** Returns the reader's rating per book among the given books, omitting books they have not rated. */
    @Transactional(readOnly = true)
    public Map<Long, Integer> ratingsOf(final long userId, final Collection<Long> bookIds) {
        if (bookIds.isEmpty()) {
            return Map.of();
        }
        return reviews.findRatedByUserForBooks(userId, bookIds).stream()
            .collect(Collectors.toMap(Review::getBookId, Review::getRating));
    }
}
