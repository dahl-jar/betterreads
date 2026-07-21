package com.betterreads.reviews.service;

import com.betterreads.common.exception.ResourceNotFoundException;
import com.betterreads.reviews.repository.ReviewRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Existence and write-lock checks on a review. */
@Service
public class ReviewLookup {

    private final ReviewRepository reviews;

    public ReviewLookup(final ReviewRepository reviews) {
        this.reviews = reviews;
    }

    /** Returns true if a review with the id exists. */
    @Transactional(readOnly = true)
    public boolean exists(final long reviewId) {
        return reviews.existsById(reviewId);
    }

    /**
     * Locks the review for update and returns its id, or throws when none matches. Runs in the
     * caller's transaction so the lock holds through the caller's own write.
     */
    public long lockAndGetId(final long reviewId) {
        return reviews.findForUpdate(reviewId)
            .orElseThrow(() -> new ResourceNotFoundException("No review with id " + reviewId))
            .getReviewId();
    }
}
