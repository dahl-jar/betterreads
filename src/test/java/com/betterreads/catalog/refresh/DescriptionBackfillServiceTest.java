package com.betterreads.catalog.refresh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.betterreads.catalog.entity.Book;
import com.betterreads.catalog.repository.BookDescriptionRepository;
import com.betterreads.catalog.service.pipeline.DescriptionSelector;
import com.betterreads.catalog.service.source.DescriptionLookup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Pageable;

/**
 * The backfill walks a bounded slice of thin-description books, re-resolves a stronger one, and
 * writes only the description column. A book the sources cannot improve is stamped as checked so the
 * next run moves past it; one failing book does not stop the rest.
 */
class DescriptionBackfillServiceTest {

    private static final String WEAK = "A short stub description that barely clears the bar, no more.";

    private static final String STRONG =
        "A full encyclopedic description of the book, its setting, and the arc of its protagonist "
        + "across the novel, well above the quality bar the merge applies.";

    private static final long BOOK_ID = 7L;

    private static final long OTHER_BOOK_ID = 99L;

    private static final String DEDUP_KEY = "OL1W";

    private static final String WORK_KEY = "OL77W";

    private static final String HARDCOVER_ID = "42";

    private static final String BOOK_DETAIL_CACHE = "bookDetails";

    private final BookDescriptionRepository books = mock(BookDescriptionRepository.class);

    private final DescriptionSelector selector = mock(DescriptionSelector.class);

    private final CacheManager cacheManager = mock(CacheManager.class);

    private final Cache bookDetails = mock(Cache.class);

    private final DescriptionBackfillService service =
        new DescriptionBackfillService(books, selector, cacheManager);

    @Test
    @DisplayName("writes a stronger description and evicts the cached detail")
    void writesImprovedDescription() {
        final Book book = bookNeedingDescription();
        when(books.findDescriptionBackfillCandidates(anyInt(), any(Pageable.class))).thenReturn(List.of(book));
        when(selector.bestDescription(any(DescriptionLookup.class), any())).thenReturn(Optional.of(STRONG));
        when(cacheManager.getCache(BOOK_DETAIL_CACHE)).thenReturn(bookDetails);

        service.backfillSlice();

        verify(books).updateDescription(eq(BOOK_ID), eq(STRONG), any(OffsetDateTime.class));
        verify(bookDetails).evict(DEDUP_KEY);
    }

    @Test
    @DisplayName("stamps a book the sources cannot improve as checked without writing a description")
    void stampsUnimprovableBook() {
        final Book book = bookNeedingDescription();
        when(books.findDescriptionBackfillCandidates(anyInt(), any(Pageable.class))).thenReturn(List.of(book));
        when(selector.bestDescription(any(DescriptionLookup.class), any())).thenReturn(Optional.empty());

        service.backfillSlice();

        verify(books).markDescriptionChecked(eq(BOOK_ID), any(OffsetDateTime.class));
        verify(books, never()).updateDescription(anyLong(), any(), any(OffsetDateTime.class));
    }

    @Test
    @DisplayName("one failing book does not stop the rest of the slice")
    void oneFailureDoesNotStopTheRest() {
        final Book failing = bookNeedingDescription();
        final Book ok = bookWithId(OTHER_BOOK_ID);
        when(books.findDescriptionBackfillCandidates(anyInt(), any(Pageable.class))).thenReturn(List.of(failing, ok));
        when(selector.bestDescription(any(DescriptionLookup.class), any()))
            .thenThrow(new DataAccessResourceFailureException("boom"))
            .thenReturn(Optional.of(STRONG));
        when(cacheManager.getCache(BOOK_DETAIL_CACHE)).thenReturn(bookDetails);

        service.backfillSlice();

        verify(books).updateDescription(eq(OTHER_BOOK_ID), eq(STRONG), any(OffsetDateTime.class));
    }

    @Test
    @DisplayName("the lookup carries the row's work key and Hardcover id for the id-keyed sources")
    void lookupCarriesTheRowsSourceIds() {
        final Book book = bookNeedingDescription();
        book.setOpenLibraryWorkKey(WORK_KEY);
        book.setHardcoverId(HARDCOVER_ID);
        when(books.findDescriptionBackfillCandidates(anyInt(), any(Pageable.class))).thenReturn(List.of(book));
        when(selector.bestDescription(any(DescriptionLookup.class), any())).thenReturn(Optional.empty());

        service.backfillSlice();

        final ArgumentCaptor<DescriptionLookup> lookup = ArgumentCaptor.forClass(DescriptionLookup.class);
        verify(selector).bestDescription(lookup.capture(), eq(WEAK));
        assertThat(lookup.getValue().openLibraryWorkKey()).isEqualTo(WORK_KEY);
        assertThat(lookup.getValue().hardcoverId()).isEqualTo(HARDCOVER_ID);
    }

    @Test
    @DisplayName("the full sweep walks every page until a short page ends it")
    void fullSweepWalksAllPages() {
        final Book first = bookWithId(1L);
        final Book second = bookWithId(2L);
        when(books.findAllKeyedBooks(any(Pageable.class)))
            .thenReturn(List.of(first))
            .thenReturn(List.of(second))
            .thenReturn(List.of());
        when(selector.bestDescription(any(DescriptionLookup.class), any())).thenReturn(Optional.of(STRONG));
        when(cacheManager.getCache(BOOK_DETAIL_CACHE)).thenReturn(bookDetails);

        service.fullSweep();

        verify(books).updateDescription(eq(1L), eq(STRONG), any(OffsetDateTime.class));
        verify(books).updateDescription(eq(2L), eq(STRONG), any(OffsetDateTime.class));
    }

    private static Book bookNeedingDescription() {
        return bookWithId(BOOK_ID);
    }

    private static Book bookWithId(final long bookId) {
        final Book book = new Book();
        book.setBookId(bookId);
        book.setDedupKey(DEDUP_KEY);
        book.setTitle("A Book");
        book.setDescription(WEAK);
        book.setIsbn("9780000000001");
        return book;
    }
}
