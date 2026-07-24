package com.betterreads.integration.hardcover.mapper;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

/** Turns a Hardcover series position into the catalog's volume number. */
final class VolumeNumber {

    private VolumeNumber() {
    }

    /**
     * Returns the position as a volume number, or empty when it is absent or fractional.
     *
     * <p>A fractional position is a prologue or split part tagged to the series, which the catalog
     * does not store as book N.
     */
    static Optional<Integer> fromPosition(final @Nullable Double position) {
        if (position == null || Double.compare(position, Math.floor(position)) != 0) {
            return Optional.empty();
        }
        return Optional.of(position.intValue());
    }
}
