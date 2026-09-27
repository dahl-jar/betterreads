package com.betterreads.ratings;

public interface ReviewLookup {

    long requireReviewId(long reviewId);

    long lockAndGetId(long reviewId);
}
