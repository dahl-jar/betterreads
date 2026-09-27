package com.betterreads.clients.hardcover;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

/** Turns a Hardcover series position into the catalog's volume number. */
public final class HardcoverVolumeNumber {

    private static final int FIRST_VOLUME = 1;

    private HardcoverVolumeNumber() {
    }

    /**
     * A fractional position is a prologue or split part tagged to the series, so only whole positions
     * from 1 up get a volume number.
     */
    public static Optional<Integer> fromPosition(final @Nullable Double position) {
        if (position == null || Double.compare(position, Math.floor(position)) != 0) {
            return Optional.empty();
        }
        return Optional.of(position.intValue()).filter(HardcoverVolumeNumber::isVolume);
    }

    public static boolean isVolume(final @Nullable Integer position) {
        return position != null && position >= FIRST_VOLUME;
    }
}
