package com.betterreads.reviews.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.betterreads.catalog.entity.Book;
import com.betterreads.catalog.repository.BookRepository;
import com.betterreads.reviews.dto.ReviewResponse;
import com.betterreads.reviews.dto.UpsertReviewRequest;
import com.betterreads.reviews.entity.Review;
import com.betterreads.reviews.mapper.ReviewMapper;
import com.betterreads.reviews.repository.RatingAggregate;
import com.betterreads.reviews.repository.ReviewRepository;

import org.jspecify.annotations.Nullable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Review writes, in their own bean so the retry gets a fresh transaction per attempt. The write and
 * the rating recompute share one transaction, and the write evicts the book's cached detail.
 */
@Component
public class ReviewWriter {

    private static final int RATING_SCALE = 2;

    private final ReviewRepository reviews;

    private final BookRepository books;

    private final ReviewMapper mapper;

    public ReviewWriter(
        final ReviewRepository reviews, final BookRepository books, final ReviewMapper mapper) {
        this.reviews = reviews;
        this.books = books;
        this.mapper = mapper;
    }

    /**
     * Creates or edits the caller's review and recomputes the book's rating.
     *
     * <p>{@code saveAndFlush} surfaces a duplicate-key conflict inside the transaction, where the
     * caller can retry it, before commit hides it.
     */
    @Transactional
    @CacheEvict(cacheNames = "bookDetails", key = "#book.dedupKey")
    public ReviewResponse upsert(final Long userId, final Book book, final UpsertReviewRequest request) {
        final Review review = reviews.findByUserIdAndBookId(userId, book.getBookId())
            .orElseGet(() -> new Review(userId, book.getBookId()));
        review.setRating(request.rating());
        review.setTitle(emptyToNull(request.title()));
        review.setBody(emptyToNull(request.body()));
        final Review saved = reviews.saveAndFlush(review);
        recomputeRating(book.getBookId());
        return mapper.toResponse(saved, book.getDedupKey());
    }

    /** Removes the caller's review of the book and recomputes the rating when a review was removed. */
    @Transactional
    @CacheEvict(cacheNames = "bookDetails", key = "#book.dedupKey")
    public void remove(final Long userId, final Book book) {
        if (reviews.deleteByUserIdAndBookId(userId, book.getBookId()) > 0) {
            recomputeRating(book.getBookId());
        }
    }

    /**
     * Recomputes the book's community rating under a row lock, so two concurrent rate writes do not
     * each persist an aggregate that misses the other's review.
     */
    private void recomputeRating(final Long bookId) {
        final Book locked = books.findForUpdate(bookId)
            .orElseThrow(() -> new IllegalStateException("rated book vanished bookId=" + bookId));
        final RatingAggregate aggregate = reviews.aggregateRatingForBook(bookId);
        final BigDecimal average = aggregate.average() == null
            ? null
            : BigDecimal.valueOf(aggregate.average()).setScale(RATING_SCALE, RoundingMode.HALF_UP);
        locked.applyCommunityAggregate(average, (int) aggregate.count());
        books.save(locked);
    }

    private static @Nullable String emptyToNull(final @Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
