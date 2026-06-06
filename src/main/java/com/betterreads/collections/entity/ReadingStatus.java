package com.betterreads.collections.entity;

/**
 * Shelf a book sits on for one user. The wire and enum form is the constant name; {@link #dbValue}
 * is the lowercase string stored in {@code user_book_collection.status}, matching the CHECK
 * constraint added in migration V9 and the vocabulary {@code user_book_interaction} already uses.
 */
@SuppressWarnings("PMD.AvoidFieldNameMatchingMethodName")
public enum ReadingStatus {

    WANT_TO_READ("want_to_read"),
    CURRENTLY_READING("currently_reading"),
    FINISHED("finished"),
    DROPPED("dropped");

    private final String dbValue;

    ReadingStatus(final String dbValue) {
        this.dbValue = dbValue;
    }

    /** Returns the lowercase value persisted in the status column. */
    public String dbValue() {
        return dbValue;
    }

    /**
     * Returns the status for the given stored value.
     *
     * @throws IllegalArgumentException if no status maps to {@code dbValue}
     */
    public static ReadingStatus fromDbValue(final String dbValue) {
        for (final ReadingStatus status : values()) {
            if (status.dbValue.equals(dbValue)) {
                return status;
            }
        }
        throw new IllegalArgumentException("no ReadingStatus for stored value: " + dbValue);
    }
}
