package com.betterreads.features.descriptionbackfill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.betterreads.book.Book;
import com.betterreads.book.BookRepository;
import com.betterreads.book.ResolvedCredit;
import com.betterreads.booksource.CreditRole;
import com.betterreads.book.BookDetailCache;
import com.betterreads.bookdescription.DescriptionSelector;
import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.DescriptionLookup;
import com.betterreads.booksource.SourceBook;
import com.betterreads.testsupport.Books;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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

    private static final String QID = "Q190192";

    private static final String ISBN = "9780000000001";

    private static final String TITLE = "A Book";

    private static final String AUTHOR = "Author";

    private static final int SLICE_SIZE = 50;

    private final BookDescriptionRepository books = mock(BookDescriptionRepository.class);

    private final BookRepository catalog = mock(BookRepository.class);

    private final DescriptionSelector selector = mock(DescriptionSelector.class);

    private final CacheManager cacheManager = mock(CacheManager.class);

    private final Cache bookDetails = mock(Cache.class);

    private final DescriptionBackfillService service =
        new DescriptionBackfillService(books, catalog, selector, cacheManager);

    @Test
    @DisplayName("writes a stronger description and evicts the cached detail")
    void writesImprovedDescription() {
        final Book book = bookWithId(BOOK_ID);
        stubThinSlice(List.of(book));
        stubImprovement();

        service.backfillSlice();

        verify(books).updateDescription(eq(BOOK_ID), eq(STRONG), any(OffsetDateTime.class));
        verify(bookDetails).evict(DEDUP_KEY);
    }

    @Test
    @DisplayName("stamps a book the sources cannot improve as checked without writing a description")
    void stampsUnimprovableBook() {
        stubUnimproved(bookWithId(BOOK_ID));

        service.backfillSlice();

        verify(books).markDescriptionChecked(eq(BOOK_ID), any(OffsetDateTime.class));
        verify(books, never()).updateDescription(anyLong(), any(), any(OffsetDateTime.class));
    }

    @Test
    @DisplayName("one failing book does not stop the rest of the slice")
    void oneFailureDoesNotStopTheRest() {
        final Book failing = bookWithId(BOOK_ID);
        final Book ok = bookWithId(OTHER_BOOK_ID);
        stubThinSlice(List.of(failing, ok));
        when(selector.bestDescription(any(DescriptionLookup.class), any()))
            .thenThrow(new DataAccessResourceFailureException("boom"))
            .thenReturn(Optional.of(STRONG));
        when(cacheManager.getCache(BookDetailCache.NAME)).thenReturn(bookDetails);

        service.backfillSlice();

        verify(books).updateDescription(eq(OTHER_BOOK_ID), eq(STRONG), any(OffsetDateTime.class));
    }

    @Test
    @DisplayName("the lookup carries the row's identifiers, title and first author")
    void lookupCarriesTheRowsSourceIds() {
        final Book book = bookWithId(BOOK_ID);
        final SourceBook.Builder source = SourceBook.builder(BookFieldSource.WIKIDATA);
        source.title(TITLE);
        source.description(WEAK);
        source.isbn13(ISBN);
        source.wikidataQid(QID);
        source.openLibraryWorkKey(WORK_KEY);
        source.hardcoverId(HARDCOVER_ID);
        book.applyFrom(source.build());
        book.replaceCredits(List.of(new ResolvedCredit(Books.author(AUTHOR), CreditRole.AUTHOR)));
        stubUnimproved(book);

        service.backfillSlice();

        final ArgumentCaptor<DescriptionLookup> lookup = ArgumentCaptor.forClass(DescriptionLookup.class);
        verify(selector).bestDescription(lookup.capture(), eq(WEAK));
        assertThat(lookup.getValue())
            .isEqualTo(new DescriptionLookup(QID, ISBN, TITLE, AUTHOR, WORK_KEY, HARDCOVER_ID));
    }

    @Test
    @DisplayName("the full sweep walks every page until an empty page ends it")
    void fullSweepWalksAllPages() {
        final Book first = bookWithId(1L);
        final Book second = bookWithId(2L);
        stubKeyedPage(0, List.of(1L), List.of(first));
        stubKeyedPage(1, List.of(2L), List.of(second));
        stubImprovement();

        service.fullSweep();

        verify(books).updateDescription(eq(1L), eq(STRONG), any(OffsetDateTime.class));
        verify(books).updateDescription(eq(2L), eq(STRONG), any(OffsetDateTime.class));
    }

    @Test
    void shouldBackfillInRankOrder() {
        final List<Long> ranked = List.of(OTHER_BOOK_ID, BOOK_ID);
        when(books.findIdsWithThinDescription(anyInt(), any(Pageable.class))).thenReturn(ranked);
        when(catalog.findByBookIdIn(ranked)).thenReturn(List.of(bookWithId(BOOK_ID), bookWithId(OTHER_BOOK_ID)));
        when(selector.bestDescription(any(DescriptionLookup.class), any())).thenReturn(Optional.empty());
        final InOrder order = inOrder(books);

        service.backfillSlice();

        order.verify(books).markDescriptionChecked(eq(OTHER_BOOK_ID), any(OffsetDateTime.class));
        order.verify(books).markDescriptionChecked(eq(BOOK_ID), any(OffsetDateTime.class));
    }

    @Test
    void shouldKeepSweepingPastPageOfDeletedBooks() {
        stubKeyedPage(0, List.of(1L), List.of());
        stubKeyedPage(1, List.of(2L), List.of(bookWithId(2L)));
        stubImprovement();

        service.fullSweep();

        verify(books).updateDescription(eq(2L), eq(STRONG), any(OffsetDateTime.class));
    }

    private void stubThinSlice(final List<Book> slice) {
        final List<Long> ids = Books.idsOf(slice);
        when(books.findIdsWithThinDescription(anyInt(), any(Pageable.class))).thenReturn(ids);
        when(catalog.findByBookIdIn(ids)).thenReturn(slice);
    }

    private void stubKeyedPage(final int page, final List<Long> ids, final List<Book> rows) {
        when(books.findIdsKeyed(PageRequest.of(page, SLICE_SIZE))).thenReturn(ids);
        when(catalog.findByBookIdIn(ids)).thenReturn(rows);
    }

    private void stubImprovement() {
        when(selector.bestDescription(any(DescriptionLookup.class), any())).thenReturn(Optional.of(STRONG));
        when(cacheManager.getCache(BookDetailCache.NAME)).thenReturn(bookDetails);
    }

    private void stubUnimproved(final Book book) {
        stubThinSlice(List.of(book));
        when(selector.bestDescription(any(DescriptionLookup.class), any())).thenReturn(Optional.empty());
    }

    private static Book bookWithId(final long bookId) {
        final Book book = Books.withId(bookId);
        book.setDedupKey(DEDUP_KEY);
        book.setTitle(TITLE);
        book.setDescription(WEAK);
        book.setIsbn(ISBN);
        return book;
    }
}
