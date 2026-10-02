package com.betterreads.booksource;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

public final class SeriesNumber {

    private static final double HUNDREDTHS = 100.0;

    private static final double LIMIT = 10_000.0;

    private SeriesNumber() {
    }

    public static Optional<Double> of(final @Nullable Double position) {
        return Optional.ofNullable(position)
            .map(value -> Math.round(value * HUNDREDTHS) / HUNDREDTHS)
            .filter(value -> value > 0 && value < LIMIT);
    }
}
