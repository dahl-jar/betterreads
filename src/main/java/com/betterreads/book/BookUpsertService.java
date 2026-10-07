package com.betterreads.book;

import java.time.OffsetDateTime;
import java.util.List;

import com.betterreads.booksource.MergedBook;
import com.betterreads.booksource.SourceAuthor;
import com.betterreads.booksource.SourceBook;

/** Upserts books and authors from merged external sources under a row lock. */
public interface BookUpsertService {

    /**
     * A source with a Hardcover id replaces the stored series. An empty series keeps it.
     *
     * <p>Two concurrent upserts of a new source key can both miss the lookup, and the second insert
     * then fails on the unique source-id column.
     */
    Book upsertFromSource(SourceBook source);

    /** The series replaces the stored one only when Hardcover returned this book. An empty series keeps it. */
    Book upsertFromSource(MergedBook merged);

    Book applyVerified(long bookId, VerifiedMetadata metadata, int checkVersion);

    boolean deferMetadataCheck(long bookId, OffsetDateTime retryAt, int maxAttempts);

    Book applyCredits(long bookId, List<SourceAuthor> credits);
}
