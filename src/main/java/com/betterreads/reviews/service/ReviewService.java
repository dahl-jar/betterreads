package com.betterreads.reviews.service;

import com.betterreads.common.dto.PageQuery;
import com.betterreads.reviews.dto.ReviewPage;
import com.betterreads.reviews.dto.ReviewResponse;
import com.betterreads.reviews.dto.UpsertReviewRequest;

/** Reads and writes user reviews and ratings, keyed per user and book. */
public interface ReviewService {

    /** Creates or edits the caller's review of the book, recomputing the book's community rating. */
    ReviewResponse upsert(Long userId, String bookKey, UpsertReviewRequest request);

    /** Removes the caller's review of the book and recomputes the rating. Idempotent. */
    void remove(Long userId, String bookKey);

    /** Returns a page of a book's reviews, newest edit first. */
    ReviewPage listForBook(String bookKey, PageQuery page);

    /** Returns a page of the caller's reviews across all books, newest edit first. */
    ReviewPage listOwn(Long userId, PageQuery page);
}
