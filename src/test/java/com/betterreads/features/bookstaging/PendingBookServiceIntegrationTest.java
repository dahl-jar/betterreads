package com.betterreads.features.bookstaging;

import com.betterreads.book.BookRepository;
import com.betterreads.bookmerge.SourceMerger;
import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.MergedBook;
import com.betterreads.booksource.SourceBook;
import com.betterreads.pendingbook.PendingBook;
import com.betterreads.pendingbook.PendingBookMapper;
import com.betterreads.pendingbook.PendingBookRepository;
import com.betterreads.pendingbook.PendingBookStatus;
import com.betterreads.testsupport.ContainerizedTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static com.betterreads.features.bookstaging.DuneBooks.AUTHOR;
import static com.betterreads.features.bookstaging.DuneBooks.ISBN;
import static com.betterreads.features.bookstaging.DuneBooks.OL_KEY;
import static com.betterreads.features.bookstaging.DuneBooks.SEQUEL_ISBN;
import static com.betterreads.features.bookstaging.DuneBooks.SERIES_POSITION;
import static com.betterreads.features.bookstaging.DuneBooks.TITLE;
import static com.betterreads.features.bookstaging.NoNetworkSources.HARDCOVER_RESPONSE;
import static com.betterreads.features.bookstaging.NoNetworkSources.OPEN_LIBRARY_RESPONSE;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "betterreads.catalog.staging.poll-enabled=false"
})
@Import(NoNetworkSources.class)
class PendingBookServiceIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String STALE_AUTHOR = "Stale Author";

    private static final String AUTHOR_NAME_FIELD = "name";

    @Autowired
    private PendingBookService pendingBookService;

    @Autowired
    private SourceMerger merger;

    @Autowired
    private PendingBookRepository pendingBooks;

    @Autowired
    private BookRepository books;

    @Autowired
    private SourceCollector sourceCollector;

    @Autowired
    private PendingBookMapper pendingBookMapper;

    @Autowired
    private PendingBookPromoter promoter;

    @Autowired
    private BookStager stager;

    @BeforeEach
    void clearCatalog() {
        pendingBooks.deleteAll();
        books.deleteAll();
        NoNetworkSources.reset();
    }

    @Test
    @DisplayName("a candidate with every required field is promoted into book")
    void readyCandidateIsPromoted() {
        pendingBookService.stage(merger.merge(null, List.of(DuneBooks.completeDune())), null);

        pendingBookService.promoteReady();

        assertThat(books.findByOpenLibraryWorkKey(OL_KEY))
            .as("a complete candidate must land in book")
            .isPresent();
        assertThat(pendingBooks.findByIsbn13(ISBN))
            .get()
            .satisfies(row -> assertThat(row.getStatus()).isEqualTo(PendingBookStatus.PROMOTED));
    }

    @Test
    @DisplayName("an incomplete candidate stays staged and never reaches book")
    void incompleteCandidateStaysStaged() {
        pendingBookService.stage(merger.merge(null, List.of(DuneBooks.sparseDune())), null);

        pendingBookService.promoteReady();

        assertThat(books.count())
            .as("a candidate missing required fields must not be promoted")
            .isZero();
        assertThat(pendingBooks.findByIsbn13(ISBN))
            .get()
            .satisfies(row -> assertThat(row.getStatus()).isEqualTo(PendingBookStatus.PENDING));
    }

    @Test
    @DisplayName("promotion takes the series and rating from the Hardcover source the collect fetches")
    void promotionTakesSeriesAndRatingFromHardcover() {
        HARDCOVER_RESPONSE.set(DuneBooks.hardcoverDune());
        pendingBookService.stage(merger.merge(null, List.of(DuneBooks.completeDune())), null);

        pendingBookService.promoteReady();

        assertThat(books.findByOpenLibraryWorkKey(OL_KEY))
            .as("Hardcover supplies the series and rating on the collect, so they reach the book")
            .get()
            .satisfies(book -> {
                assertThat(book.getSeriesName()).isEqualTo(TITLE);
                assertThat(book.getSeriesPosition()).isEqualTo(SERIES_POSITION);
                assertThat(book.getAverageRating()).isNotNull();
            });
    }

    @Test
    @DisplayName("a candidate colliding with a promoted work is retired and the poll reaches the rest")
    void collidingCandidateIsRetiredAndPollContinues() {
        pendingBookService.stage(merger.merge(null, List.of(DuneBooks.completeDune())), null);
        pendingBookService.promoteReady();
        pendingBookService.stage(merger.merge(null, List.of(DuneBooks.secondEditionDune())), null);
        pendingBookService.stage(merger.merge(null, List.of(DuneBooks.duneMessiah())), null);
        OPEN_LIBRARY_RESPONSE.set(DuneBooks.collidingSecondEdition());

        pendingBookService.promoteReady();

        assertThat(pendingBooks.findByDedupKey(DuneBooks.SECOND_EDITION_ISBN))
            .as("the second edition resolves a work key another row owns, and no retry changes that")
            .get()
            .satisfies(row -> assertThat(row.getStatus()).isEqualTo(PendingBookStatus.DUPLICATE));
        assertThat(books.findByDedupKey(SEQUEL_ISBN))
            .as("the candidate behind the colliding one must still be promoted")
            .isPresent();
    }

    @Test
    @DisplayName("an attempted candidate is not collected again until the retry window passes")
    void attemptedCandidateWaitsForTheRetryWindow() {
        pendingBookService.stage(merger.merge(null, List.of(DuneBooks.sparseDune())), null);
        pendingBookService.promoteReady();

        OPEN_LIBRARY_RESPONSE.set(DuneBooks.openLibraryCompleteDune());
        pendingBookService.promoteReady();

        assertThat(pendingBooks.findByIsbn13(ISBN))
            .as("the candidate was attempted moments ago, so the poll must skip it this cycle")
            .get()
            .satisfies(row -> assertThat(row.getStatus()).isEqualTo(PendingBookStatus.PENDING));
    }

    @Test
    @DisplayName("a two-author book comes back with each subject once")
    void fetchReturnsEachSubjectOnceForTwoAuthorBook() {
        pendingBookService.stage(merger.merge(null, List.of(DuneBooks.duneBy(AUTHOR, STALE_AUTHOR))), null);
        pendingBookService.promoteReady();

        assertThat(books.findByDedupKey(ISBN))
            .as("the read fetch joins authors and subjects, and the join must not repeat subject rows")
            .get()
            .satisfies(book -> assertThat(book.getSubjects())
                .extracting("subject")
                .containsExactly(DuneBooks.GENRE));
    }

    private void due(final int attempts, final Duration lastAttemptAgo) {
        final PendingBook row = pendingBooks.findByIsbn13(ISBN).orElseThrow();
        row.setAttemptCount(attempts);
        row.setLastAttemptAt(OffsetDateTime.now(ZoneOffset.UTC).minus(lastAttemptAgo));
        pendingBooks.save(row);
    }

    @Nested
    class RePromotion {

        @Test
        void shouldKeepSeriesAfterAHardcoverMiss() {
            HARDCOVER_RESPONSE.set(DuneBooks.hardcoverDune());
            pendingBookService.stage(merger.merge(null, List.of(DuneBooks.completeDune())), null);
            pendingBookService.promoteReady();
            HARDCOVER_RESPONSE.set(DuneBooks.hardcoverDune().toBuilder().isbn13(SEQUEL_ISBN).build());
            NoNetworkSources.WIKIDATA_RESPONSE.set(DuneBooks.wikidataDuneChronicles());

            rePromote();

            assertThat(books.findByOpenLibraryWorkKey(OL_KEY))
                .get()
                .satisfies(book -> assertThat(book.getSeriesName()).isEqualTo(TITLE));
        }

        @Test
        @DisplayName("re-promotion keeps a real series when Hardcover fails")
        void rePromotionKeepsSeriesWhenHardcoverFails() {
            HARDCOVER_RESPONSE.set(DuneBooks.hardcoverDune());
            pendingBookService.stage(merger.merge(null, List.of(DuneBooks.completeDune())), null);
            pendingBookService.promoteReady();

            HARDCOVER_RESPONSE.set(null);
            rePromote();

            assertThat(books.findByOpenLibraryWorkKey(OL_KEY))
                .as("Hardcover did not resolve, so the existing series is kept")
                .get()
                .satisfies(book -> {
                    assertThat(book.getSeriesName()).isEqualTo(TITLE);
                    assertThat(book.getSeriesPosition()).isEqualTo(SERIES_POSITION);
                });
        }

        @Test
        @DisplayName("re-promotion corrects a stale staged author from a live OpenLibrary fetch")
        void rePromotionDropsStaleAuthor() {
            pendingBookService.stage(merger.merge(null, List.of(DuneBooks.duneBy(AUTHOR, STALE_AUTHOR))), null);
            pendingBookService.promoteReady();

            OPEN_LIBRARY_RESPONSE.set(DuneBooks.openLibraryDune());
            rePromote();

            assertThat(books.findByOpenLibraryWorkKey(OL_KEY))
                .as("the live OpenLibrary fetch names one author, so the stale staged one is removed")
                .get()
                .satisfies(book -> assertThat(book.getAuthors())
                    .extracting(AUTHOR_NAME_FIELD)
                    .containsExactly(AUTHOR));
        }

        @Test
        @DisplayName("a re-promotion with every live source down keeps the staged book intact")
        void allSourcesDownRePromotionKeepsStagedBook() {
            pendingBookService.stage(merger.merge(null, List.of(DuneBooks.completeDune())), null);
            pendingBookService.promoteReady();

            rePromote();

            assertThat(books.findByOpenLibraryWorkKey(OL_KEY))
                .as("no live source resolved, so the staged values still promote the book unchanged")
                .get()
                .satisfies(book -> {
                    assertThat(book.getTitle()).isEqualTo(TITLE);
                    assertThat(book.getAuthors())
                        .extracting(AUTHOR_NAME_FIELD)
                        .containsExactly(AUTHOR);
                });
        }

        private void rePromote() {
            final PendingBook row = pendingBooks.findByIsbn13(ISBN).orElseThrow();
            final MergedBook collected = sourceCollector.collectFor(pendingBookMapper.toSourceBook(row));
            promoter.promote(ISBN, collected);
        }
    }

    @Nested
    class Backoff {

        @ParameterizedTest(name = "{0} attempts, {1} min ago -> {2}")
        @CsvSource({
            "0, 16, PROMOTED", "1, 14, PENDING", "1, 16, PROMOTED", "2, 50, PENDING", "2, 61, PROMOTED",
            "3, 300, PENDING", "3, 361, PROMOTED", "4, 420, PENDING", "4, 1441, PROMOTED"})
        void shouldRetryAfterTheBackoffForItsAttemptCount(
            final int attempts, final long minutesAgo, final String expected) {
            pendingBookService.stage(merger.merge(null, List.of(DuneBooks.sparseDune())), null);
            due(attempts, Duration.ofMinutes(minutesAgo));
            OPEN_LIBRARY_RESPONSE.set(DuneBooks.openLibraryCompleteDune());

            pendingBookService.promoteReady();

            assertThat(pendingBooks.findByIsbn13(ISBN))
                .get()
                .satisfies(row -> assertThat(row.getStatus()).isEqualTo(expected));
        }
    }

    @Nested
    class FirstTitle {

        private static final String FIRST_TITLE = "Hollow Throne";

        private static final String STORED_TITLE = "Hollow Throne: The Life of a Queen";

        private static final String RETRY_TITLE = "Hollow Throne: Book One of the Wars";

        private static final String RETRY_SERIES = "Wars";

        private static final int RETRY_ATTEMPTS = 1;

        private static final Duration RETRY_WAIT = Duration.ofMinutes(16);

        @Test
        void shouldKeepHitWhenSourceFirstAnswersOnRetry() {
            stageSeed(FIRST_TITLE);
            HARDCOVER_RESPONSE.set(retryHit());

            retry();

            assertThat(pendingBooks.findByIsbn13(ISBN))
                .get()
                .satisfies(row -> assertThat(row.getSeriesName()).isEqualTo(RETRY_SERIES));
        }

        @Test
        void shouldKeepFirstTitleOnRetry() {
            stageSeed(FIRST_TITLE);
            HARDCOVER_RESPONSE.set(retryHit());

            retry();

            assertThat(pendingBooks.findByIsbn13(ISBN))
                .get()
                .satisfies(row -> assertThat(row.getFirstTitle()).isEqualTo(FIRST_TITLE));
        }

        @Test
        void shouldKeepFirstTitleWhenUntitledRowIsStagedAgain() {
            final MergedBook untitled = merger.merge(null, List.of(DuneBooks.completeDune()))
                .withBook(SourceBook.builder(BookFieldSource.OPEN_LIBRARY).isbn13(ISBN).build());
            pendingBookService.stage(untitled, FIRST_TITLE);

            pendingBookService.stage(untitled, RETRY_TITLE);

            assertThat(pendingBooks.findByIsbn13(ISBN))
                .get()
                .satisfies(row -> assertThat(row.getFirstTitle()).isEqualTo(FIRST_TITLE));
        }

        @Test
        void shouldDropRetryHitWithAnotherSubtitleWhenSeedHadNoTitle() {
            stageSeed(null);
            HARDCOVER_RESPONSE.set(retryHit());

            retry();

            assertThat(pendingBooks.findByIsbn13(ISBN))
                .get()
                .satisfies(row -> {
                    assertThat(row.getFirstTitle()).isNull();
                    assertThat(row.getSeriesName()).isNull();
                });
        }

        private void stageSeed(final @Nullable String seedTitle) {
            OPEN_LIBRARY_RESPONSE.set(SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
                .isbn13(ISBN)
                .title(STORED_TITLE)
                .build());
            stager.stage(SourceBook.builder(BookFieldSource.WIKIDATA).isbn13(ISBN).title(seedTitle).build());
        }

        private static SourceBook retryHit() {
            return SourceBook.builder(BookFieldSource.HARDCOVER)
                .isbn13(ISBN)
                .title(RETRY_TITLE)
                .seriesName(RETRY_SERIES)
                .seriesPosition(SERIES_POSITION)
                .build();
        }

        private void retry() {
            due(RETRY_ATTEMPTS, RETRY_WAIT);
            pendingBookService.promoteReady();
        }
    }
}
