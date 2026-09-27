package com.betterreads.features.reviews;

import com.betterreads.web.PageQuery;

/** Reads and writes user reviews and ratings, keyed per user and book. */
interface ReviewService {

    /** Also recomputes the book's community rating. */
    ReviewResponse upsert(Long userId, String bookKey, UpsertReviewRequest request);

    /** Also recomputes the community rating. Removing a missing review does nothing. */
    void remove(Long userId, String bookKey);

    /** Newest edit first. */
    ReviewPage listForBook(String bookKey, PageQuery page);

    /** Newest edit first. */
    ReviewPage listOwn(Long userId, PageQuery page);

    CommunityRatingResponse communityRating(String bookKey);
}
