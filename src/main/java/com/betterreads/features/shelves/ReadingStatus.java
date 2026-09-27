package com.betterreads.features.shelves;

import java.util.Arrays;

/**
 * Shelf a book sits on for one user. The API uses the constant name, the status column stores the
 * lowercase form the V9 CHECK constraint allows.
 */
enum ReadingStatus {

    WANT_TO_READ("want_to_read"),
    CURRENTLY_READING("currently_reading"),
    FINISHED("finished"),
    DROPPED("dropped");

    private final String stored;

    ReadingStatus(final String stored) {
        this.stored = stored;
    }

    public String dbValue() {
        return stored;
    }

    public static ReadingStatus fromDbValue(final String dbValue) {
        return Arrays.stream(values())
            .filter(status -> status.stored.equals(dbValue))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("no ReadingStatus for stored value: " + dbValue));
    }
}
