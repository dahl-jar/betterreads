package com.betterreads.features.metadatacheck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.LongStream;

import com.betterreads.book.Author;
import com.betterreads.book.Book;
import com.betterreads.book.BookUpsertService;
import com.betterreads.book.VerifiedMetadata;
import com.betterreads.booksource.SeriesEntry;
import com.betterreads.clients.websearch.CheckedBook;
import com.betterreads.clients.websearch.MetadataCheckClient;
import com.betterreads.clients.websearch.MetadataCheckRequest;
import com.betterreads.clients.websearch.MetadataJson;
import com.betterreads.clients.websearch.SeriesBook;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.domain.Pageable;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class MetadataCheckServiceTest {

    private static final long BOOK_ID = MetadataJson.BOOK_ID;

    private static final long OTHER_ID = MetadataJson.OTHER_ID;

    private static final int YEAR = MetadataJson.YEAR;

    private static final String TITLE = MetadataJson.TITLE;

    private static final String AUTHOR = MetadataJson.AUTHOR;

    private static final String SERIES = MetadataJson.SERIES;

    private static final String ISBN = MetadataJson.ISBN;

    private static final String LAST_KING = "The Last King of Osten Ard";

    private static final SeriesEntry UNIVERSE = MetadataJson.UNIVERSE_ENTRY;

    private static final String GOLDEN_SON = MetadataJson.GOLDEN_SON;

    private static final int LOGGED_LINE_LIMIT = 4000;

    private static final String END_MARKER = "END";

    private static final VerifiedMetadata CORRECTED =
        new VerifiedMetadata(TITLE, List.of(AUTHOR), YEAR, SERIES, 1.0, null, ISBN, null);

    private final MetadataCheckRepository books = mock(MetadataCheckRepository.class);

    private final MetadataCheckClient client = mock(MetadataCheckClient.class);

    private final BookUpsertService upsert = mock(BookUpsertService.class);

    private final MetadataCheckService service =
        new MetadataCheckService(books, client, upsert, MetadataCheckSamples.properties(true));

    private void givenBooks(final List<Book> found) {
        when(books.findDueForCheck(any(Pageable.class))).thenReturn(found);
    }

    private static List<Book> booksNumbered(final int count) {
        return LongStream.rangeClosed(1, count).mapToObj(MetadataCheckServiceTest::book).toList();
    }

    @Nested
    class Batching {

        @Test
        void shouldSendTenBooksPerCall() {
            givenBooks(booksNumbered(2 * MetadataCheckSamples.BATCH_SIZE));
            givenAnswers(Map.of());
            final ArgumentCaptor<List<MetadataCheckRequest>> batches = ArgumentCaptor.captor();

            service.checkDueBooks();

            verify(client, times(2)).check(batches.capture());
            assertThat(batches.getAllValues()).extracting(List::size)
                .containsExactly(MetadataCheckSamples.BATCH_SIZE, MetadataCheckSamples.BATCH_SIZE);
        }

        @Test
        void shouldSkipTheNameQueriesWithoutBooks() {
            givenBooks(List.of());

            service.checkDueBooks();

            verify(books, never()).findSeriesNames();
        }

        @Test
        void shouldStopOnFailedBatch() {
            givenBooks(booksNumbered(2 * MetadataCheckSamples.BATCH_SIZE));
            when(client.check(any())).thenReturn(Optional.empty());

            service.checkDueBooks();

            verify(client, times(1)).check(any());
            verify(upsert, never()).applyVerified(anyLong(), any());
        }

        @Test
        void shouldCapBooksPerRun() {
            givenBooks(List.of());
            final ArgumentCaptor<Pageable> page = ArgumentCaptor.captor();

            service.checkDueBooks();

            verify(books).findDueForCheck(page.capture());
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
            book.applySeries(List.of(new SeriesEntry(SERIES, 1), UNIVERSE), true);
            givenBooks(List.of(book));
            when(books.findSeriesBooks(SERIES, BOOK_ID)).thenReturn(List.of(new SeriesBook(GOLDEN_SON, 2.0)));
            givenAnswers(Map.of());
            final ArgumentCaptor<List<MetadataCheckRequest>> batch = ArgumentCaptor.captor();

            service.checkDueBooks();

            verify(client).check(batch.capture());
            assertThat(batch.getValue()).containsExactly(new MetadataCheckRequest(BOOK_ID, TITLE, List.of(AUTHOR),
                YEAR, SERIES, 1.0, ISBN, UNIVERSE, List.of(new SeriesBook(GOLDEN_SON, 2.0))));
        }

        @Test
        void shouldApplyCorrections() {
            givenBooks(List.of(book(BOOK_ID)));
            givenAnswers(Map.of(BOOK_ID, MetadataCheckSamples.confirmed(CORRECTED)));

            service.checkDueBooks();

            verify(upsert).applyVerified(BOOK_ID, CORRECTED);
        }

        @Test
        void shouldUseStoredSeriesSpelling() {
            givenBooks(List.of(book(BOOK_ID)));
            when(books.findSeriesNames()).thenReturn(List.of(LAST_KING));
            final VerifiedMetadata found =
                new VerifiedMetadata(null, null, null, "last king of osten ard", 1.0, null, null, null);

            checkWithAnswer(found);

            verify(upsert).applyVerified(BOOK_ID,
                new VerifiedMetadata(null, null, null, LAST_KING, 1.0, null, null, null));
        }

        @Test
        void shouldUseStoredAuthorSpelling() {
            givenBooks(List.of(book(BOOK_ID)));
            when(books.findAuthorNames()).thenReturn(List.of(AUTHOR));
            final VerifiedMetadata found =
                new VerifiedMetadata(null, List.of("BROWN, Pierce"), null, null, null, null, null, null);

            checkWithAnswer(found);

            verify(upsert).applyVerified(BOOK_ID,
                new VerifiedMetadata(null, List.of(AUTHOR), null, null, null, null, null, null));
        }

        @Test
        void shouldKeepStoredTitleForSubtitle() {
            givenBooks(List.of(book(BOOK_ID)));
            final VerifiedMetadata found = titled("Red Rising: Book One of the Red Rising Saga");

            checkWithAnswer(found);

            verify(upsert).applyVerified(BOOK_ID, titled(TITLE));
        }

        private static VerifiedMetadata titled(final String title) {
            return new VerifiedMetadata(title, null, null, null, null, null, null, null);
        }

        @Test
        void shouldMarkUnansweredBookChecked() {
            givenBooks(List.of(book(BOOK_ID)));
            givenAnswers(Map.of());

            service.checkDueBooks();

            verify(upsert).applyVerified(BOOK_ID, VerifiedMetadata.NONE);
        }

        @Test
        void shouldContinueAfterFailedBook() {
            givenBooks(List.of(book(BOOK_ID), book(OTHER_ID)));
            givenAnswers(Map.of());
            when(upsert.applyVerified(BOOK_ID, VerifiedMetadata.NONE)).thenThrow(new IllegalArgumentException("gone"));

            service.checkDueBooks();

            verify(upsert).applyVerified(OTHER_ID, VerifiedMetadata.NONE);
        }

        private void checkWithAnswer(final VerifiedMetadata found) {
            givenAnswers(Map.of(BOOK_ID, MetadataCheckSamples.confirmed(found)));
            service.checkDueBooks();
        }
    }

    @Nested
    @ExtendWith(OutputCaptureExtension.class)
    class Logging {

        @Test
        void shouldLogTheOutcomesOfEachAnsweredBook(final CapturedOutput output) {
            givenBooks(List.of(book(BOOK_ID)));
            givenAnswers(Map.of(BOOK_ID, MetadataCheckSamples.confirmed(CORRECTED)));

            service.checkDueBooks();

            assertThat(output.getOut())
                .contains("catalog.metadata-check bookId=1 outcomes=title:CONFIRMED,authors:CONFIRMED,"
                    + "year:CONFIRMED,series:CONFIRMED,universe:CONFIRMED,description:CONFIRMED,isbn13:CONFIRMED"
                    + " answer={\"id\":1,\"title\":");
        }

        @Test
        void shouldLogEachBatch(final CapturedOutput output) {
            givenBooks(List.of(book(BOOK_ID)));
            givenAnswers(Map.of(BOOK_ID, MetadataCheckSamples.confirmed(CORRECTED)));

            service.checkDueBooks();

            assertThat(output.getOut())
                .contains("catalog.metadata-check batch books=1 turns=3 costUsd=0.25 durationMs=35210 deniedCalls=0");
        }

        @Test
        void shouldCutALongAnswerLine(final CapturedOutput output) {
            final ObjectNode longAnswer = new JsonMapper().createObjectNode()
                .put("title", "x".repeat(LOGGED_LINE_LIMIT) + END_MARKER);
            givenBooks(List.of(book(BOOK_ID)));
            givenAnswers(Map.of(BOOK_ID, new CheckedBook(CORRECTED, Map.of(), longAnswer)));

            service.checkDueBooks();

            assertThat(output.getOut()).contains("x".repeat(LOGGED_LINE_LIMIT / 2)).doesNotContain(END_MARKER);
        }

        @Test
        void shouldLogARunSummary(final CapturedOutput output) {
            givenBooks(booksNumbered(MetadataCheckSamples.BATCH_SIZE + 1));
            givenAnswers(Map.of(BOOK_ID, MetadataCheckSamples.confirmed(CORRECTED),
                OTHER_ID, MetadataCheckSamples.unconfirmed()));

            service.checkDueBooks();

            assertThat(output.getOut())
                .contains("catalog.metadata-check done books=11 confirmed=1 unconfirmed=10 turns=6 costUsd=0.50");
        }

        @Test
        void shouldSkipTheSummaryWhenASearchFails(final CapturedOutput output) {
            givenBooks(booksNumbered(2 * MetadataCheckSamples.BATCH_SIZE));
            when(client.check(any())).thenReturn(Optional.empty());

            service.checkDueBooks();

            assertThat(output.getOut())
                .contains("catalog.metadata-check stopped, the search failed")
                .doesNotContain("catalog.metadata-check done");
        }
    }

    private void givenAnswers(final Map<Long, CheckedBook> answers) {
        when(client.check(any())).thenReturn(Optional.of(MetadataCheckSamples.run(answers)));
    }

    private static Book book(final long bookId) {
        final Book book = new Book();
        book.setBookId(bookId);
        book.setDedupKey("book-" + bookId);
        book.setTitle(TITLE);
        return book;
    }
}
