package com.betterreads.features.search;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import org.jspecify.annotations.Nullable;

public record AuthorSearchDocument(
    long authorId,
    String name,
    String surname,
    @JsonSetter(nulls = Nulls.AS_EMPTY) List<String> aliases,
    @Nullable String photoUrl,
    int bookCount,
    double popularityScore,
    @JsonSetter(nulls = Nulls.AS_EMPTY) List<String> topTitles
) {

    static final String PRIMARY_KEY = "authorId";

    public AuthorSearchDocument {
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
