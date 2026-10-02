package com.betterreads.features.booklists;

import java.util.List;

import org.jspecify.annotations.Nullable;

record SeriesBookResponse(
    String key,
    String title,
    List<String> authors,
    @Nullable String coverUrl,
    int position
) {

    public SeriesBookResponse {
        authors = List.copyOf(authors);
    }

    @Override
    public List<String> authors() {
        return List.copyOf(authors);
    }
}
