package com.betterreads.features.reviews;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.betterreads.bookaccess.BookCommunityRatingWriter;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Review writes, in their own bean so each upsert retry gets a fresh transaction. The review and the
 * rating recompute commit together.
 */
@Component
class ReviewWriter {

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

    /** saveAndFlush raises the duplicate-key conflict before commit, so the caller can retry it. */
    @Transactional
    public ReviewResponse upsert(
        final Long userId, final long bookId, final String bookKey,
        final UpsertReviewRequest request) {
        final Review review = reviews.findByUserIdAndBookId(userId, bookId)
            .orElseGet(() -> new Review(userId, bookId));
        review.setRating(request.rating());
        review.setTitle(blankToNull(request.title()));
        review.setBody(blankToNull(request.body()));
        final Review saved = reviews.saveAndFlush(review);
        recomputeRating(bookId);
        return mapper.toResponse(saved, bookKey);
    }

    @Transactional
    public void remove(final Long userId, final long bookId) {
        if (reviews.deleteByUserIdAndBookId(userId, bookId) > 0) {
            recomputeRating(bookId);
        }
    }

    private void recomputeRating(final long bookId) {
        final ReviewRatingAggregate aggregate = reviews.aggregateRatingForBook(bookId);
        final BigDecimal average = aggregate.average() == null
            ? null
            : BigDecimal.valueOf(aggregate.average()).setScale(RATING_SCALE, RoundingMode.HALF_UP);
        communityRating.applyCommunityAggregate(bookId, average, (int) aggregate.count());
    }

    private static @Nullable String blankToNull(final @Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
