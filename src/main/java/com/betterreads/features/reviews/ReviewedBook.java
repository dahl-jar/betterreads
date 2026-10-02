package com.betterreads.features.reviews;

import java.util.List;

import org.jspecify.annotations.Nullable;

public record ReviewedBook(String key, String title, List<String> authors, @Nullable String coverUrl) {

    public ReviewedBook {
        authors = List.copyOf(authors);
    }

    @Override
    public List<String> authors() {
        return List.copyOf(authors);
    }
}
