package com.betterreads.booksource;

import java.util.List;

import org.jspecify.annotations.Nullable;

public record SeriesEntry(String name, int position) {

    public static List<SeriesEntry> listOf(final @Nullable String name, final @Nullable Integer position) {
        return name == null || position == null ? List.of() : List.of(new SeriesEntry(name, position));
    }
}
