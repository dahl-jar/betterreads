package com.betterreads.bookindex;

import java.util.List;

import org.jspecify.annotations.Nullable;

public record AuthorIndexView(
    long authorId,
    String name,
    List<String> aliases,
    @Nullable String photoUrl,
    int bookCount,
    double popularityScore,
    List<String> topTitles
) {

    public AuthorIndexView {
        aliases = List.copyOf(aliases);
        topTitles = List.copyOf(topTitles);
    }

    @Override
    public List<String> aliases() {
        return List.copyOf(aliases);
    }

    @Override
    public List<String> topTitles() {
        return List.copyOf(topTitles);
    }
}
