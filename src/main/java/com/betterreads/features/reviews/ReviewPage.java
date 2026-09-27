package com.betterreads.features.reviews;

import com.betterreads.web.Paged;

import java.util.List;

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
