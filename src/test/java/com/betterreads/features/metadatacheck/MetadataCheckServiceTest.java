package com.betterreads.features.metadatacheck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import com.betterreads.book.Book;
import com.betterreads.book.ResolvedCredit;
import com.betterreads.booksource.CreditRole;
import com.betterreads.book.BookUpsertService;
import com.betterreads.book.VerifiedMetadata;
import com.betterreads.booksource.SeriesEntry;
import com.betterreads.testsupport.Books;
import com.betterreads.clients.websearch.CheckOutcome;
import com.betterreads.clients.websearch.CheckedBook;
import com.betterreads.clients.websearch.MetadataCheckClient;
import com.betterreads.clients.websearch.MetadataCheckRequest;
import com.betterreads.clients.websearch.MetadataJson;
import com.betterreads.clients.websearch.SeriesBook;
import com.betterreads.clients.websearch.SourceGroup;
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

    private static final int VERSION = MetadataCheckSamples.CHECK_VERSION;

    private static final String TITLE = MetadataJson.TITLE;

    private static final String AUTHOR = MetadataJson.AUTHOR;

    private static final String CO_AUTHOR = "Ann Leckie";

    private static final String SERIES = MetadataJson.SERIES;

    private static final String ISBN = MetadataJson.ISBN;

    private static final String LAST_KING = "The Last King of Osten Ard";

    private static final String GONE = "gone";

    private static final SeriesEntry UNIVERSE = MetadataJson.UNIVERSE_ENTRY;

    private static final String GOLDEN_SON = MetadataJson.GOLDEN_SON;

    private static final int LOGGED_LINE_LIMIT = 4000;

    private static final String END_MARKER = "END";

    private static final int MAX_ATTEMPTS = MetadataCheckSamples.MAX_ATTEMPTS;

    private static final CheckOutcome FAILED = new CheckOutcome.BatchFailed("exit 1");

    private static final CheckOutcome HALTED = new CheckOutcome.RunHalted("You have hit your session limit");

    private static final VerifiedMetadata CORRECTED =
        new VerifiedMetadata(TITLE, List.of(AUTHOR), YEAR, SERIES, 1.0, null, ISBN, null);

    private final MetadataCheckRepository books = mock(MetadataCheckRepository.class);

    private final MetadataCheckClient client = mock(MetadataCheckClient.class);

    private final BookUpsertService upsert = mock(BookUpsertService.class);

    private final MetadataCheckService service =
        new MetadataCheckService(books, client, upsert, MetadataCheckSamples.properties(true));

    private void givenBooks(final List<Book> found) {
        when(books.findDueForCheck(any(OffsetDateTime.class), any(Pageable.class))).thenReturn(found);
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

            verify(client, times(2)).check(batches.capture(), any());
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
        void shouldContinueAfterAFailedBatch() {
            givenBooks(booksNumbered(2 * MetadataCheckSamples.BATCH_SIZE));
            when(client.check(any(), any())).thenReturn(FAILED, MetadataCheckSamples.outcome(Map.of()));

            service.checkDueBooks();

            verify(client, times(2)).check(any(), any());
        }

        @Test
        void shouldStopTheRunOnAUsageLimit() {
            givenBooks(booksNumbered(2 * MetadataCheckSamples.BATCH_SIZE));
            when(client.check(any(), any())).thenReturn(HALTED);

            service.checkDueBooks();

            verify(client, times(1)).check(any(), any());
            verify(upsert).deferMetadataCheck(eq(BOOK_ID), any(), eq(MAX_ATTEMPTS));
            verify(upsert, never()).deferMetadataCheck(eq(MetadataCheckSamples.BATCH_SIZE + 1L), any(), anyInt());
        }

        @Test
        void shouldBatchBooksBySourceGroup() {
            givenBooks(List.of(MetadataCheckSamples.withGenre(book(BOOK_ID), "Comics"),
                MetadataCheckSamples.withGenre(book(OTHER_ID), "Fantasy")));
            givenAnswers(Map.of());
            final ArgumentCaptor<SourceGroup> groups = ArgumentCaptor.captor();

            service.checkDueBooks();

            verify(client, times(2)).check(any(), groups.capture());
            assertThat(groups.getAllValues()).containsExactly(SourceGroup.COMIC, SourceGroup.SFF);
        }

        @Test
        void shouldCapBooksPerRun() {
            givenBooks(List.of());
            final ArgumentCaptor<Pageable> page = ArgumentCaptor.captor();

            service.checkDueBooks();

            verify(books).findDueForCheck(any(OffsetDateTime.class), page.capture());
            assertThat(page.getValue().getPageSize()).isEqualTo(MetadataCheckSamples.MAX_BOOKS);
        }
    }

    @Nested
    class Applying {

        @Test
        void shouldSendBookDetails() {
            final Book book = book(BOOK_ID);
            book.replaceCredits(List.of(
                new ResolvedCredit(Books.author(AUTHOR), CreditRole.AUTHOR),
                new ResolvedCredit(Books.author(CO_AUTHOR), CreditRole.AUTHOR)));
            book.setFirstPublishYear(YEAR);
            book.setIsbn(ISBN);
            book.applySeries(List.of(new SeriesEntry(SERIES, 1), UNIVERSE), true);
            givenBooks(List.of(book));
            when(books.findSeriesBooks(SERIES, BOOK_ID)).thenReturn(List.of(new SeriesBook(GOLDEN_SON, 2.0)));
            givenAnswers(Map.of());
            final ArgumentCaptor<List<MetadataCheckRequest>> batch = ArgumentCaptor.captor();

            service.checkDueBooks();

            verify(client).check(batch.capture(), any());
            assertThat(batch.getValue()).containsExactly(new MetadataCheckRequest(
                BOOK_ID, TITLE, List.of(AUTHOR, CO_AUTHOR), YEAR, SERIES, 1.0, ISBN, UNIVERSE,
                List.of(new SeriesBook(GOLDEN_SON, 2.0)), null));
        }

        @Test
        void shouldApplyCorrections() {
            checkConfirmedBook();

            verify(upsert).applyVerified(BOOK_ID, CORRECTED, VERSION);
        }

        @Test
        void shouldUseStoredSeriesSpelling() {
            givenBooks(List.of(book(BOOK_ID)));
            when(books.findSeriesNames()).thenReturn(List.of(LAST_KING));
            final VerifiedMetadata found =
                new VerifiedMetadata(null, null, null, "last king of osten ard", 1.0, null, null, null);

            checkWithAnswer(found);

            verify(upsert).applyVerified(BOOK_ID,
                new VerifiedMetadata(null, null, null, LAST_KING, 1.0, null, null, null), VERSION);
        }

        @Test
        void shouldUseStoredAuthorSpelling() {
            givenBooks(List.of(book(BOOK_ID)));
            when(books.findAuthorNames()).thenReturn(List.of(AUTHOR));
            final VerifiedMetadata found =
                new VerifiedMetadata(null, List.of("BROWN, Pierce"), null, null, null, null, null, null);

            checkWithAnswer(found);

            verify(upsert).applyVerified(BOOK_ID,
                new VerifiedMetadata(null, List.of(AUTHOR), null, null, null, null, null, null), VERSION);
        }

        @Test
        void shouldKeepStoredTitleForSubtitle() {
            givenBooks(List.of(book(BOOK_ID)));
            final VerifiedMetadata found = titled("Red Rising: Book One of the Red Rising Saga");

            checkWithAnswer(found);

            verify(upsert).applyVerified(BOOK_ID, titled(TITLE), VERSION);
        }

        private static VerifiedMetadata titled(final String title) {
            return new VerifiedMetadata(title, null, null, null, null, null, null, null);
        }

        @Test
        void shouldPassTheEvidenceToTheUpsert() {
            givenBooks(List.of(book(BOOK_ID)));
            final VerifiedMetadata found = MetadataCheckSamples.yearWithEvidence();

            checkWithAnswer(found);

            verify(upsert).applyVerified(BOOK_ID, found, VERSION);
        }

        @Test
        void shouldApplyAClearWithNoOtherField() {
            givenBooks(List.of(book(BOOK_ID)));
            final VerifiedMetadata cleared = MetadataCheckSamples.clearsOnly();

            checkWithAnswer(cleared);

            verify(upsert).applyVerified(BOOK_ID, cleared, VERSION);
            verify(upsert, never()).deferMetadataCheck(anyLong(), any(), anyInt());
        }

        @Test
        void shouldContinueAfterFailedBook() {
            givenBooks(List.of(book(BOOK_ID), book(OTHER_ID)));
            givenAnswers(Map.of(BOOK_ID, MetadataCheckSamples.confirmed(CORRECTED),
                OTHER_ID, MetadataCheckSamples.confirmed(CORRECTED)));
            when(upsert.applyVerified(BOOK_ID, CORRECTED, VERSION)).thenThrow(new IllegalArgumentException(GONE));

            service.checkDueBooks();

            verify(upsert).applyVerified(OTHER_ID, CORRECTED, VERSION);
        }

        private void checkWithAnswer(final VerifiedMetadata found) {
            givenAnswers(Map.of(BOOK_ID, MetadataCheckSamples.confirmed(found)));
            service.checkDueBooks();
        }
    }

    @Nested
    class Deferring {

        @Test
        void shouldContinueAfterAFailedDeferral() {
            givenBooks(List.of(book(BOOK_ID), book(OTHER_ID)));
            givenAnswers(Map.of(BOOK_ID, MetadataCheckSamples.unconfirmed(),
                OTHER_ID, MetadataCheckSamples.unconfirmed()));
            when(upsert.deferMetadataCheck(eq(BOOK_ID), any(), anyInt()))
                .thenThrow(new IllegalArgumentException(GONE));

            service.checkDueBooks();

            verify(upsert).deferMetadataCheck(eq(OTHER_ID), any(), eq(MAX_ATTEMPTS));
        }

        @Test
        void shouldDeferABookWithNothingVerified() {
            givenBooks(List.of(book(BOOK_ID)));
            givenAnswers(Map.of(BOOK_ID, MetadataCheckSamples.unconfirmed()));

            service.checkDueBooks();

            verify(upsert, never()).applyVerified(anyLong(), any(), anyInt());
            assertThat(deferredUntil())
                .isCloseTo(MetadataCheckSamples.after(MetadataCheckSamples.RETRY_AFTER_UNCONFIRMED),
                    MetadataCheckSamples.A_MINUTE);
        }

        @Test
        void shouldDeferTheBooksOfAFailedBatchByADay() {
            givenBooks(List.of(book(BOOK_ID)));
            when(client.check(any(), any())).thenReturn(FAILED);

            service.checkDueBooks();

            assertThat(deferredUntil())
                .isCloseTo(MetadataCheckSamples.after(MetadataCheckSamples.RETRY_AFTER_FAILURE),
                    MetadataCheckSamples.A_MINUTE);
        }

        private OffsetDateTime deferredUntil() {
            final ArgumentCaptor<OffsetDateTime> retryAt = ArgumentCaptor.captor();
            verify(upsert).deferMetadataCheck(eq(BOOK_ID), retryAt.capture(), eq(MAX_ATTEMPTS));
            return retryAt.getValue();
        }
    }

    @Nested
    @ExtendWith(OutputCaptureExtension.class)
    class Logging {

        @Test
        void shouldLogTheOutcomesOfEachAnsweredBook(final CapturedOutput output) {
            checkConfirmedBook();

            assertThat(output.getOut())
                .contains("catalog.metadata-check bookId=1 outcomes=title:ACCEPTED,authors:ACCEPTED,"
                    + "year:ACCEPTED,series:ACCEPTED,universe:ACCEPTED,description:ACCEPTED,isbn13:ACCEPTED"
                    + " answer={\"id\":1,\"title\":");
        }

        @Test
        void shouldLogEachBatch(final CapturedOutput output) {
            checkConfirmedBook();

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
            when(upsert.deferMetadataCheck(anyLong(), any(), anyInt())).thenReturn(true);

            service.checkDueBooks();

            assertThat(output.getOut())
                .contains("catalog.metadata-check done books=11 verified=1 deferred=10 gaveUp=0 turns=6 costUsd=0.50");
        }

        @Test
        void shouldLogOutcomeCountsInTheSummary(final CapturedOutput output) {
            givenBooks(List.of(book(BOOK_ID), book(OTHER_ID)));
            givenAnswers(Map.of(BOOK_ID, MetadataCheckSamples.confirmed(CORRECTED),
                OTHER_ID, MetadataCheckSamples.unconfirmed()));

            service.checkDueBooks();

            assertThat(output.getOut()).contains("outcomes=ACCEPTED:7,NOT_ANSWERED:7");
        }

        @Test
        void shouldLogASummaryWhenTheRunHalts(final CapturedOutput output) {
            givenBooks(List.of(book(BOOK_ID)));
            when(client.check(any(), any())).thenReturn(HALTED);

            service.checkDueBooks();

            assertThat(output.getOut()).contains("catalog.metadata-check done books=1");
        }

        @Test
        void shouldLeaveABookThatFailedToApplyOutOfTheVerifiedCount(final CapturedOutput output) {
            givenBooks(List.of(book(BOOK_ID)));
            givenAnswers(Map.of(BOOK_ID, MetadataCheckSamples.confirmed(CORRECTED)));
            when(upsert.applyVerified(BOOK_ID, CORRECTED, VERSION)).thenThrow(new IllegalArgumentException(GONE));

            service.checkDueBooks();

            assertThat(output.getOut()).contains("verified=0");
        }

        @Test
        void shouldCountBooksThatGaveUp(final CapturedOutput output) {
            givenBooks(List.of(book(BOOK_ID)));
            givenAnswers(Map.of(BOOK_ID, MetadataCheckSamples.unconfirmed()));
            when(upsert.deferMetadataCheck(anyLong(), any(), anyInt())).thenReturn(false);

            service.checkDueBooks();

            assertThat(output.getOut()).contains("deferred=0 gaveUp=1");
        }

        @Test
        void shouldCountUnreachableHostsInTheSummary(final CapturedOutput output) {
            givenBooks(List.of(book(BOOK_ID)));
            final CheckedBook unreachable =
                MetadataCheckSamples.unreachable(MetadataJson.YEAR_FIELD, MetadataJson.SOURCE);
            givenAnswers(Map.of(BOOK_ID, unreachable));

            service.checkDueBooks();

            assertThat(output.getOut()).contains("unreachable=en.wikipedia.org:1");
        }
    }

    private void givenAnswers(final Map<Long, CheckedBook> answers) {
        when(client.check(any(), any())).thenReturn(MetadataCheckSamples.outcome(answers));
    }

    private static Book book(final long bookId) {
        final Book book = new Book();
        book.setBookId(bookId);
        book.setDedupKey("book-" + bookId);
        book.setTitle(TITLE);
        return book;
    }

    private void checkConfirmedBook() {
        givenBooks(List.of(book(BOOK_ID)));
        givenAnswers(Map.of(BOOK_ID, MetadataCheckSamples.confirmed(CORRECTED)));
        service.checkDueBooks();
    }
}
