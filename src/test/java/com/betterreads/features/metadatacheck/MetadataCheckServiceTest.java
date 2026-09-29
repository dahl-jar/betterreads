package com.betterreads.features.metadatacheck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.LongStream;

import com.betterreads.book.Author;
import com.betterreads.book.Book;
import com.betterreads.book.BookUpsertService;
import com.betterreads.book.VerifiedMetadata;
import com.betterreads.clients.websearch.MetadataCheckClient;
import com.betterreads.clients.websearch.MetadataCheckRequest;
import com.betterreads.clients.websearch.MetadataJson;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

class MetadataCheckServiceTest {

    private static final long BOOK_ID = MetadataJson.BOOK_ID;

    private static final long OTHER_ID = 2L;

    private static final int YEAR = MetadataJson.YEAR;

    private static final String TITLE = MetadataJson.TITLE;

    private static final String AUTHOR = MetadataJson.AUTHOR;

    private static final String SERIES = MetadataJson.SERIES;

    private static final String ISBN = MetadataJson.ISBN;

    private static final String LAST_KING = "The Last King of Osten Ard";

    private static final VerifiedMetadata CORRECTED =
        new VerifiedMetadata(TITLE, List.of(AUTHOR), YEAR, SERIES, 1, null, ISBN);

    private final MetadataCheckRepository books = mock(MetadataCheckRepository.class);

    private final MetadataCheckClient client = mock(MetadataCheckClient.class);

    private final BookUpsertService upsert = mock(BookUpsertService.class);

    private final MetadataCheckService service =
        new MetadataCheckService(books, client, upsert, MetadataCheckSamples.properties(true));

    private void givenBooks(final List<Book> found) {
        when(books.findUncheckedSince(any(OffsetDateTime.class), any(Pageable.class))).thenReturn(found);
    }

    private static List<Book> booksNumbered(final int count) {
        return LongStream.rangeClosed(1, count).mapToObj(MetadataCheckServiceTest::book).toList();
    }

    @Nested
    class Batching {

        @Test
        void shouldSendTenBooksPerCall() {
            givenBooks(booksNumbered(2 * MetadataCheckSamples.BATCH_SIZE));
            when(client.check(any())).thenReturn(Optional.of(Map.of()));
            final ArgumentCaptor<List<MetadataCheckRequest>> batches = ArgumentCaptor.captor();

            service.checkNewBooks();

            verify(client, times(2)).check(batches.capture());
            assertThat(batches.getAllValues()).extracting(List::size)
                .containsExactly(MetadataCheckSamples.BATCH_SIZE, MetadataCheckSamples.BATCH_SIZE);
        }

        @Test
        void shouldSkipSearchWithoutBooks() {
            givenBooks(List.of());

            service.checkNewBooks();

            verify(client, never()).check(any());
        }

        @Test
        void shouldStopOnFailedBatch() {
            givenBooks(booksNumbered(2 * MetadataCheckSamples.BATCH_SIZE));
            when(client.check(any())).thenReturn(Optional.empty());

            service.checkNewBooks();

            verify(client, times(1)).check(any());
            verify(upsert, never()).applyVerified(anyLong(), any());
        }

        @Test
        void shouldQueryLastWeek() {
            givenBooks(List.of());
            final ArgumentCaptor<OffsetDateTime> since = ArgumentCaptor.captor();
            final OffsetDateTime expected =
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(MetadataCheckSamples.LOOKBACK_DAYS);

            service.checkNewBooks();

            verify(books).findUncheckedSince(since.capture(), any(Pageable.class));
            assertThat(since.getValue()).isBetween(expected.minusMinutes(1), expected.plusMinutes(1));
        }

        @Test
        void shouldCapBooksPerRun() {
            givenBooks(List.of());
            final ArgumentCaptor<Pageable> page = ArgumentCaptor.captor();

            service.checkNewBooks();

            verify(books).findUncheckedSince(any(OffsetDateTime.class), page.capture());
            assertThat(page.getValue().getPageSize()).isEqualTo(MetadataCheckSamples.MAX_BOOKS);
        }
    }

    @Nested
    class Applying {

        @Test
        void shouldSendBookDetails() {
            final Book book = book(BOOK_ID);
            final Author author = new Author();
            author.setName(AUTHOR);
            book.setAuthors(Set.of(author));
            book.setFirstPublishYear(YEAR);
            book.setIsbn(ISBN);
            book.applySeries(SERIES, 1, true);
            givenBooks(List.of(book));
            when(client.check(any())).thenReturn(Optional.of(Map.of()));
            final ArgumentCaptor<List<MetadataCheckRequest>> batch = ArgumentCaptor.captor();

            service.checkNewBooks();

            verify(client).check(batch.capture());
            assertThat(batch.getValue())
                .containsExactly(new MetadataCheckRequest(BOOK_ID, TITLE, List.of(AUTHOR), YEAR, SERIES, 1, ISBN));
        }

        @Test
        void shouldApplyCorrections() {
            givenBooks(List.of(book(BOOK_ID)));
            when(client.check(any())).thenReturn(Optional.of(Map.of(BOOK_ID, CORRECTED)));

            service.checkNewBooks();

            verify(upsert).applyVerified(BOOK_ID, CORRECTED);
        }

        @Test
        void shouldUseStoredSeriesSpelling() {
            givenBooks(List.of(book(BOOK_ID)));
            when(books.findSeriesNames()).thenReturn(List.of(LAST_KING));
            final VerifiedMetadata found =
                new VerifiedMetadata(null, null, null, "last king of osten ard", 1, null, null);
            when(client.check(any())).thenReturn(Optional.of(Map.of(BOOK_ID, found)));

            service.checkNewBooks();

            verify(upsert).applyVerified(BOOK_ID,
                new VerifiedMetadata(null, null, null, LAST_KING, 1, null, null));
        }

        @Test
        void shouldUseStoredAuthorSpelling() {
            givenBooks(List.of(book(BOOK_ID)));
            when(books.findAuthorNames()).thenReturn(List.of(AUTHOR));
            final VerifiedMetadata found =
                new VerifiedMetadata(null, List.of("BROWN, Pierce"), null, null, null, null, null);
            when(client.check(any())).thenReturn(Optional.of(Map.of(BOOK_ID, found)));

            service.checkNewBooks();

            verify(upsert).applyVerified(BOOK_ID,
                new VerifiedMetadata(null, List.of(AUTHOR), null, null, null, null, null));
        }

        @Test
        void shouldKeepStoredTitleForSubtitle() {
            givenBooks(List.of(book(BOOK_ID)));
            final VerifiedMetadata found =
                new VerifiedMetadata("Red Rising: Book One of the Red Rising Saga", null, null, null, null, null, null);
            when(client.check(any())).thenReturn(Optional.of(Map.of(BOOK_ID, found)));

            service.checkNewBooks();

            verify(upsert).applyVerified(BOOK_ID, new VerifiedMetadata(TITLE, null, null, null, null, null, null));
        }

        @Test
        void shouldMarkUnansweredBookChecked() {
            givenBooks(List.of(book(BOOK_ID)));
            when(client.check(any())).thenReturn(Optional.of(Map.of()));

            service.checkNewBooks();

            verify(upsert).applyVerified(BOOK_ID, VerifiedMetadata.NONE);
        }

        @Test
        void shouldContinueAfterFailedBook() {
            givenBooks(List.of(book(BOOK_ID), book(OTHER_ID)));
            when(client.check(any())).thenReturn(Optional.of(Map.of()));
            when(upsert.applyVerified(BOOK_ID, VerifiedMetadata.NONE)).thenThrow(new IllegalArgumentException("gone"));

            service.checkNewBooks();

            verify(upsert).applyVerified(OTHER_ID, VerifiedMetadata.NONE);
        }
    }

    private static Book book(final long bookId) {
        final Book book = new Book();
        book.setBookId(bookId);
        book.setDedupKey("book-" + bookId);
        book.setTitle(TITLE);
        return book;
    }
}
