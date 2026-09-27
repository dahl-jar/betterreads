package com.betterreads.features.reviews;

import org.springframework.stereotype.Component;

@Component
class ReviewMapper {

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
