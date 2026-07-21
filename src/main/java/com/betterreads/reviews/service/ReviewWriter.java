package com.betterreads.reviews.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.betterreads.catalog.service.write.BookCommunityRatingWriter;
import com.betterreads.reviews.dto.ReviewResponse;
import com.betterreads.reviews.dto.UpsertReviewRequest;
import com.betterreads.reviews.entity.Review;
import com.betterreads.reviews.mapper.ReviewMapper;
import com.betterreads.reviews.repository.RatingAggregate;
import com.betterreads.reviews.repository.ReviewRepository;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Review writes, in their own bean so the retry gets a fresh transaction per attempt. The write and
 * the rating recompute share one transaction; the catalog aggregate write evicts the book's detail.
 */
@Component
public class ReviewWriter {

    private static final int RATING_SCALE = 2;

    private final ReviewRepository reviews;

    private final BookCommunityRatingWriter communityRating;

    private final ReviewMapper mapper;

    public ReviewWriter(
        final ReviewRepository reviews,
        final BookCommunityRatingWriter communityRating,
        final ReviewMapper mapper) {
        this.reviews = reviews;
        this.communityRating = communityRating;
        this.mapper = mapper;
    }

    /**
     * Creates or edits the caller's review and recomputes the book's rating.
     *
     * <p>{@code saveAndFlush} surfaces a duplicate-key conflict inside the transaction, where the
     * caller can retry it, before commit hides it.
     */
    @Transactional
    public ReviewResponse upsert(
        final Long userId, final long bookId, final String dedupKey,
        final UpsertReviewRequest request) {
        final Review review = reviews.findByUserIdAndBookId(userId, bookId)
            .orElseGet(() -> new Review(userId, bookId));
        review.setRating(request.rating());
        review.setTitle(emptyToNull(request.title()));
        review.setBody(emptyToNull(request.body()));
        final Review saved = reviews.saveAndFlush(review);
        recomputeRating(bookId);
        return mapper.toResponse(saved, dedupKey);
    }

    /** Removes the caller's review of the book and recomputes the rating when a review was removed. */
    @Transactional
    public void remove(final Long userId, final long bookId) {
        if (reviews.deleteByUserIdAndBookId(userId, bookId) > 0) {
            recomputeRating(bookId);
        }
    }

    private void recomputeRating(final long bookId) {
        final RatingAggregate aggregate = reviews.aggregateRatingForBook(bookId);
        final BigDecimal average = aggregate.average() == null
            ? null
            : BigDecimal.valueOf(aggregate.average()).setScale(RATING_SCALE, RoundingMode.HALF_UP);
        communityRating.applyCommunityAggregate(bookId, average, (int) aggregate.count());
    }

    private static @Nullable String emptyToNull(final @Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
