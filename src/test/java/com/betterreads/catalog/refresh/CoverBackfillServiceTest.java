package com.betterreads.catalog.refresh;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.betterreads.catalog.entity.Book;
import com.betterreads.catalog.repository.BookCoverRepository;
import com.betterreads.catalog.service.source.CoverMirrorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Pageable;

/**
 * The cover backfill walks a bounded slice of un-mirrored promoted books, mirrors each cover, and
 * records the object key. A cover the sources cannot mirror is stamped as checked so the next run
 * moves past it; one failing book does not stop the rest.
 */
class CoverBackfillServiceTest {

    private static final String KEY = "OL1W";

    private static final long BOOK_ID = 3L;

    private static final long OTHER_ID = 8L;

    private static final String COVER_URL = "https://covers.example.org/1.jpg";

    private static final String OBJECT_KEY = "covers/OL1W";

    private final BookCoverRepository books = mock(BookCoverRepository.class);

    private final CoverMirrorService coverMirror = mock(CoverMirrorService.class);

    private final CoverBackfillService service = new CoverBackfillService(books, coverMirror);

    @Test
    @DisplayName("records the object key for a book whose cover mirrors")
    void recordsMirroredCover() {
        when(books.findCoverBackfillCandidates(any(Pageable.class))).thenReturn(List.of(book(BOOK_ID)));
        when(coverMirror.mirror(KEY, COVER_URL)).thenReturn(Optional.of(OBJECT_KEY));

        service.backfillSlice();

        verify(books).markCoverMirrored(eq(BOOK_ID), eq(OBJECT_KEY), any(OffsetDateTime.class));
    }

    @Test
    @DisplayName("stamps a book the sources cannot mirror as checked")
    void stampsUnmirrorableCover() {
        when(books.findCoverBackfillCandidates(any(Pageable.class))).thenReturn(List.of(book(BOOK_ID)));
        when(coverMirror.mirror(KEY, COVER_URL)).thenReturn(Optional.empty());

        service.backfillSlice();

        verify(books).markCoverChecked(eq(BOOK_ID), any(OffsetDateTime.class));
    }

    @Test
    @DisplayName("one failing book does not stop the rest of the slice")
    void oneFailureDoesNotStopTheRest() {
        when(books.findCoverBackfillCandidates(any(Pageable.class)))
            .thenReturn(List.of(book(BOOK_ID), book(OTHER_ID)));
        when(coverMirror.mirror(KEY, COVER_URL))
            .thenThrow(new DataAccessResourceFailureException("boom"))
            .thenReturn(Optional.of(OBJECT_KEY));

        service.backfillSlice();

        verify(books).markCoverMirrored(eq(OTHER_ID), eq(OBJECT_KEY), any(OffsetDateTime.class));
    }

    @Test
    @DisplayName("the full sweep walks every page until a short page ends it")
    void fullSweepWalksAllPages() {
        when(books.findCoverSweepCandidates(any(OffsetDateTime.class), any(Pageable.class)))
            .thenReturn(List.of(book(BOOK_ID)))
            .thenReturn(List.of(book(OTHER_ID)))
            .thenReturn(List.of());
        when(coverMirror.mirror(KEY, COVER_URL)).thenReturn(Optional.of(OBJECT_KEY));

        service.fullSweep();

        verify(books).markCoverMirrored(eq(BOOK_ID), eq(OBJECT_KEY), any(OffsetDateTime.class));
        verify(books).markCoverMirrored(eq(OTHER_ID), eq(OBJECT_KEY), any(OffsetDateTime.class));
    }

    private static Book book(final long bookId) {
        final Book book = new Book();
        book.setBookId(bookId);
        book.setDedupKey(KEY);
        book.setCoverUrl(COVER_URL);
        return book;
    }
}
