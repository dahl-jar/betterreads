package com.betterreads.features.reviews;

import com.betterreads.errors.ResourceNotFoundException;
import com.betterreads.ratings.ReviewLookup;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Existence and write-lock checks on a review. */
@Service
class ReviewLookupImpl implements ReviewLookup {

    private final ReviewRepository reviews;

    ReviewLookupImpl(final ReviewRepository reviews) {
        this.reviews = reviews;
    }

    @Override
    @Transactional(readOnly = true)
    public long requireReviewId(final long reviewId) {
        if (!reviews.existsById(reviewId)) {
            throw notFound(reviewId);
        }
        return reviewId;
    }

    /** Runs in the caller's transaction, so the row lock holds until the caller's write commits. */
    @Override
    public long lockAndGetId(final long reviewId) {
        return reviews.findForUpdate(reviewId)
            .orElseThrow(() -> notFound(reviewId))
            .getReviewId();
    }

    private static ResourceNotFoundException notFound(final long reviewId) {
        return new ResourceNotFoundException("No review with id " + reviewId);
    }
}
