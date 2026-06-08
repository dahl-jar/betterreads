package com.betterreads.reviews.dto;

import com.betterreads.common.dto.Paged;

import java.util.List;

/**
 * A page of reviews.
 *
 * @param reviews the reviews on this page, in listing order
 * @param total the total number of reviews across all pages
 * @param offset the zero-based offset of the first review on this page
 * @param limit the page size
 */
public record ReviewPage(
    List<ReviewResponse> reviews,
    long total,
    int offset,
    int limit
) implements Paged<ReviewResponse> {

    public ReviewPage {
        reviews = List.copyOf(reviews);
    }

    @Override
    public List<ReviewResponse> reviews() {
        return List.copyOf(reviews);
    }

    @Override
    public List<ReviewResponse> items() {
        return reviews();
    }
}
