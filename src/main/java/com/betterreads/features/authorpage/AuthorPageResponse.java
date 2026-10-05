package com.betterreads.features.authorpage;

import java.util.List;

import org.jspecify.annotations.Nullable;

public record AuthorPageResponse(
    long authorId,
    String name,
    @Nullable String photoUrl,
    @Nullable String bio,
    List<AuthorBookResponse> books
) {

    public AuthorPageResponse {
        books = List.copyOf(books);
    }

    @Override
    public List<AuthorBookResponse> books() {
        return List.copyOf(books);
    }
}
