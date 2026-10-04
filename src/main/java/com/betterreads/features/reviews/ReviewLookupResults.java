package com.betterreads.features.reviews;

import java.util.Map;

import com.betterreads.shelfaccess.ReaderBook;
import com.betterreads.shelfaccess.ReadingDates;
import com.betterreads.users.UsernameLookup;

record ReviewLookupResults(
    Map<Long, String> authors,
    Map<Long, Long> commentCounts,
    Map<ReaderBook, ReadingDates> readingDates
) {

    static ReaderBook readerBookOf(final Review review) {
        return new ReaderBook(review.getUserId(), review.getBookId());
    }

    String authorOf(final Review review) {
        return UsernameLookup.usernameIn(this.authors, review.getUserId());
    }

    long commentCountOf(final Review review) {
        return this.commentCounts.getOrDefault(review.getReviewId(), 0L);
    }

    ReadingDates readingDatesOf(final Review review) {
        return this.readingDates.getOrDefault(readerBookOf(review), ReadingDates.NONE);
    }
}
