package com.betterreads.reviews.mapper;

import com.betterreads.reviews.dto.ReviewResponse;
import com.betterreads.reviews.entity.Review;

import org.springframework.stereotype.Component;

/** Builds a {@link ReviewResponse} from a review row and its book key. */
@Component
public class ReviewMapper {

    /** Combines the review with the public {@code bookKey} the review's book is addressed by. */
    public ReviewResponse toResponse(final Review review, final String bookKey) {
        return new ReviewResponse(
            review.getReviewId(),
            bookKey,
            review.getRating(),
            review.getTitle(),
            review.getBody(),
            review.getCreatedAt().toLocalDate());
    }
}
