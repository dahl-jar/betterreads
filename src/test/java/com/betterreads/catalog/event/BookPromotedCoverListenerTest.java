package com.betterreads.catalog.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.Executor;

import com.betterreads.catalog.entity.Book;
import com.betterreads.catalog.repository.BookCoverRepository;
import com.betterreads.catalog.service.source.CoverMirrorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * After a book is promoted, the listener mirrors its cover and records the object key. A book the
 * sources cannot mirror is stamped as checked, and a mirror failure does not propagate.
 */
class BookPromotedCoverListenerTest {

    private static final String KEY = "OL1W";

    private static final long BOOK_ID = 5L;

    private static final String COVER_URL = "https://covers.example.org/1.jpg";

    private static final String OBJECT_KEY = "covers/OL1W";

    private final BookCoverRepository books = mock(BookCoverRepository.class);

    private final CoverMirrorService coverMirror = mock(CoverMirrorService.class);

    private final Executor sameThread = Runnable::run;

    private final BookPromotedCoverListener listener =
        new BookPromotedCoverListener(books, coverMirror, sameThread);

    @Test
    @DisplayName("a mirrored cover records its object key")
    void recordsObjectKey() {
        when(books.findByDedupKey(KEY)).thenReturn(Optional.of(book()));
        when(coverMirror.mirror(KEY, COVER_URL)).thenReturn(Optional.of(OBJECT_KEY));

        listener.onBookPromoted(new BookPromotedEvent(KEY));

        verify(books).markCoverMirrored(eq(BOOK_ID), eq(OBJECT_KEY), any(OffsetDateTime.class));
    }

    @Test
    @DisplayName("a cover the sources cannot mirror is stamped as checked")
    void stampsUnmirrorableCover() {
        when(books.findByDedupKey(KEY)).thenReturn(Optional.of(book()));
        when(coverMirror.mirror(KEY, COVER_URL)).thenReturn(Optional.empty());

        listener.onBookPromoted(new BookPromotedEvent(KEY));

        verify(books).markCoverChecked(eq(BOOK_ID), any(OffsetDateTime.class));
    }

    @Test
    @DisplayName("a mirror failure does not propagate out of the listener")
    void mirrorFailureIsContained() {
        when(books.findByDedupKey(KEY)).thenReturn(Optional.of(book()));
        when(coverMirror.mirror(KEY, COVER_URL))
            .thenThrow(new DataAccessResourceFailureException("boom"));

        listener.onBookPromoted(new BookPromotedEvent(KEY));

        verify(books, never()).markCoverMirrored(eq(BOOK_ID), any(), any(OffsetDateTime.class));
    }

    private static Book book() {
        final Book book = new Book();
        book.setBookId(BOOK_ID);
        book.setDedupKey(KEY);
        book.setCoverUrl(COVER_URL);
        return book;
    }
}
