package com.betterreads.features.reviews;

import com.betterreads.ratings.ReaderRatings;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ReaderRatingsImpl implements ReaderRatings {

    private final ReviewRepository reviews;

    ReaderRatingsImpl(final ReviewRepository reviews) {
        this.reviews = reviews;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Integer> ratingOf(final long userId, final long bookId) {
        return reviews.findByUserIdAndBookId(userId, bookId).map(Review::getRating);
    }

    /** Books the reader has not rated are missing from the map. */
    @Override
    @Transactional(readOnly = true)
    public Map<Long, Integer> ratingsOf(final long userId, final Collection<Long> bookIds) {
        if (bookIds.isEmpty()) {
            return Map.of();
        }
        return reviews.findRatedByUserForBooks(userId, bookIds).stream()
            .collect(Collectors.toMap(Review::getBookId, Review::getRating));
    }
}
