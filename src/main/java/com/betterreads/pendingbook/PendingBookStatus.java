package com.betterreads.pendingbook;

// PMD.DataClass: the public fields are the pending_book.status values, there is no behavior to add.
@SuppressWarnings("PMD.DataClass")
public final class PendingBookStatus {

    public static final String PENDING = "PENDING";

    public static final String PROMOTED = "PROMOTED";

    public static final String DUPLICATE = "DUPLICATE";

    public static final String INCOMPLETE_FINAL = "INCOMPLETE_FINAL";

    private PendingBookStatus() {
    }
}
