package com.betterreads.book;

import com.betterreads.booksource.MergedBook;
import com.betterreads.booksource.SourceBook;

/** Upserts books and authors from merged external sources under a row lock. */
public interface BookUpsertService {

    /**
     * Takes the source's series as authoritative, so a missing series clears the stored one.
     *
     * <p>Two concurrent upserts of a new source key can both miss the lookup, and the second insert
     * then fails on the unique source-id column.
     */
    Book upsertFromSource(SourceBook source);

    /** The series replaces the stored one, including a clear, only when Hardcover resolved on the collect. */
    Book upsertFromSource(MergedBook merged);
}
