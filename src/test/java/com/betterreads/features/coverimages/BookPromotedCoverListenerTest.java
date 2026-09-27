package com.betterreads.features.coverimages;

import static com.betterreads.features.coverimages.CoverBackfillServiceTest.COVER_URL;
import static com.betterreads.features.coverimages.CoverBackfillServiceTest.KEY;
import static com.betterreads.features.coverimages.CoverBackfillServiceTest.OBJECT_KEY;
import static com.betterreads.features.coverimages.CoverBackfillServiceTest.book;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.Executor;

import com.betterreads.book.Book;
import com.betterreads.book.BookPromotedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class BookPromotedCoverListenerTest {

    private static final long BOOK_ID = 5L;

    private final BookCoverRepository books = mock(BookCoverRepository.class);

    private final CoverMirrorService coverMirror = mock(CoverMirrorService.class);

    private final Executor sameThread = Runnable::run;

    private final BookPromotedCoverListener listener =
        new BookPromotedCoverListener(books, new CoverBackfillService(books, coverMirror), sameThread);

    @Test
    @DisplayName("a mirrored cover records its object key")
    void recordsObjectKey() {
        when(books.findByDedupKey(KEY)).thenReturn(Optional.of(book(BOOK_ID)));
        when(coverMirror.mirror(KEY, COVER_URL)).thenReturn(Optional.of(OBJECT_KEY));

        listener.onBookPromoted(new BookPromotedEvent(KEY));

        verify(books).markCoverMirrored(eq(BOOK_ID), eq(OBJECT_KEY), any(OffsetDateTime.class));
    }

    @Test
    @DisplayName("a promoted book with no cover url is never sent to the mirror")
    void skipsBookWithoutCover() {
        final Book coverless = book(BOOK_ID);
        coverless.setCoverUrl(null);
        when(books.findByDedupKey(KEY)).thenReturn(Optional.of(coverless));

        listener.onBookPromoted(new BookPromotedEvent(KEY));

        verify(coverMirror, never()).mirror(any(), any());
    }

    @Test
    @DisplayName("a failed book lookup does not propagate out of the listener")
    void shouldContainFailedBookLookup() {
        when(books.findByDedupKey(KEY)).thenThrow(new DataAccessResourceFailureException("boom"));
        final BookPromotedEvent event = new BookPromotedEvent(KEY);

        assertThatNoException().isThrownBy(() -> listener.onBookPromoted(event));
    }
}
