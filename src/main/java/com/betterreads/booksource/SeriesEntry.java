package com.betterreads.booksource;

import java.math.BigDecimal;
import java.util.List;

import org.jspecify.annotations.Nullable;

public record SeriesEntry(String name, double position) {

    public static List<SeriesEntry> listOf(final @Nullable String name, final @Nullable Double position) {
        return name == null || position == null ? List.of() : List.of(new SeriesEntry(name, position));
    }

    public String label() {
        return name + " #" + BigDecimal.valueOf(position).stripTrailingZeros().toPlainString();
    }
}
