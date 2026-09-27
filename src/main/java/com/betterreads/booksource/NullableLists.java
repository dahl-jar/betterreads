package com.betterreads.booksource;

import java.util.List;

import org.jspecify.annotations.Nullable;

public final class NullableLists {

    private NullableLists() {
    }

    public static <T> @Nullable List<T> copyOf(final @Nullable List<T> values) {
        return values == null ? null : List.copyOf(values);
    }
}
