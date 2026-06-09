package com.betterreads.catalog.pipeline;

import com.betterreads.support.ContainerizedTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import com.betterreads.catalog.entity.PendingBook;
import com.betterreads.catalog.mapper.PendingBookMapper;
import com.betterreads.catalog.repository.BookRepository;
import com.betterreads.catalog.repository.PendingBookRepository;
import com.betterreads.catalog.service.read.CatalogService;
import com.betterreads.catalog.service.source.BookFieldSource;
import com.betterreads.catalog.service.source.BookSourceClient;
import com.betterreads.catalog.service.source.MergedBook;
import com.betterreads.catalog.service.pipeline.DescriptionSelector;
import com.betterreads.catalog.service.pipeline.PendingBookService;
import com.betterreads.catalog.service.source.SourceAuthor;
import com.betterreads.catalog.service.source.SourceBook;
import com.betterreads.catalog.service.pipeline.SourceCollector;
import com.betterreads.catalog.service.source.SourceMerger;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Staging and promotion against a real Postgres: a merged book is staged into {@code pending_book};
 * a candidate with every required field is promoted into {@code book}; an incomplete one stays
 * staged and never reaches {@code book}.
 */
// PMD.TooManyMethods, PMD.ExcessiveImports: a staging and promotion suite with a controllable source stub.
@SuppressWarnings({"PMD.TooManyMethods", "PMD.ExcessiveImports"})
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "jwt.secret=integration-test-secret-must-be-at-least-256-bits-long-padding-padding",
    "jwt.issuer=betterreads-it",
    "betterreads.catalog.staging.poll-enabled=false"
})
@Import(PendingBookServiceIntegrationTest.NoNetworkSources.class)
class PendingBookServiceIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String ISBN = "9780441013593";

    private static final String OL_KEY = "OL893415W";

    private static final int YEAR = 1965;

    private static final int SERIES_POSITION = 1;

    private static final double RATING = 4.25;

    private static final String TITLE = "Dune";

    private static final String AUTHOR = "Frank Herbert";

    private static final String STALE_AUTHOR = "Valka";

    private static final String AUTHOR_NAME_FIELD = "name";

    private static final String GENRE = "science fiction";

    private static final String COVER_URL = "https://covers.openlibrary.org/b/id/1-L.jpg";

    private static final String DESCRIPTION =
        "A desert planet holds the universe's only source of the spice melange.";

    private static final String SECOND_EDITION_ISBN = "9780345298591";

    private static final String SEQUEL_ISBN = "9780441172696";

    private static final String SEQUEL_TITLE = "Dune Messiah";

    private static final String STATUS_PENDING = "PENDING";

    private static final String STATUS_PROMOTED = "PROMOTED";

    private static final String STATUS_DUPLICATE = "DUPLICATE";

    private static final String STATUS_INCOMPLETE_FINAL = "INCOMPLETE_FINAL";

    private static final int STAGING_THREADS = 4;

    private static final int HTTP_SERVER_ERROR = 503;

    private static final AtomicReference<@Nullable SourceBook> HARDCOVER_RESPONSE =
        new AtomicReference<>();

    private static final AtomicReference<@Nullable SourceBook> OPEN_LIBRARY_RESPONSE =
        new AtomicReference<>();

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
    private CatalogService catalogService;

    @Autowired
    private PendingBookMapper pendingBookMapper;

    @BeforeEach
    void clearCatalog() {
        pendingBooks.deleteAll();
        books.deleteAll();
        HARDCOVER_RESPONSE.set(null);
        OPEN_LIBRARY_RESPONSE.set(null);
    }

    @Test
    @DisplayName("staging a merged book writes one pending row")
    void stagingWritesPendingRow() {
        final MergedBook merged = merger.merge(List.of(completeDune()));

        pendingBookService.stage(merged);

        assertThat(pendingBooks.findByIsbn13(ISBN))
            .isPresent()
            .get()
            .satisfies(row -> {
                assertThat(row.getTitle()).isEqualTo(TITLE);
                assertThat(row.getStatus()).isEqualTo(STATUS_PENDING);
            });
    }

    @Test
    @DisplayName("staging the same book twice updates the row rather than inserting a second")
    void stagingTwiceUpdatesInPlace() {
        pendingBookService.stage(merger.merge(List.of(sparseDune())));

        pendingBookService.stage(merger.merge(List.of(completeDune())));

        assertThat(pendingBooks.count())
            .as("the second stage of the same ISBN must reuse the existing row")
            .isEqualTo(1L);
        assertThat(pendingBooks.findByIsbn13(ISBN))
            .get()
            .satisfies(row -> assertThat(row.getCoverUrl()).isNotNull());
    }

    // PMD.DoNotUseThreads is a J2EE-webapp rule; this test needs real threads to force the race.
    @SuppressWarnings("PMD.DoNotUseThreads")
    @Test
    @DisplayName("concurrent staging of the same new book ends with one row, no error escapes")
    void concurrentStagingOfSameBookKeepsOneRow() {
        final CountDownLatch start = new CountDownLatch(1);
        final List<DataIntegrityViolationException> races = new CopyOnWriteArrayList<>();
        final List<Future<?>> staged = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(STAGING_THREADS)) {
            for (int i = 0; i < STAGING_THREADS; i++) {
                staged.add(pool.submit(() -> stageOnSignal(start, races)));
            }
            start.countDown();
        }
        assertThat(staged).hasSize(STAGING_THREADS);

        assertThat(races)
            .as("the staging reserve must absorb the race, not surface a duplicate-key error")
            .isEmpty();
        assertThat(pendingBooks.count())
            .as("the dedup key must collapse the concurrent stages to one row")
            .isEqualTo(1L);
    }

    // PMD.DoNotUseThreads is a J2EE-webapp rule; this race test runs work on a thread pool.
    @SuppressWarnings("PMD.DoNotUseThreads")
    private void stageOnSignal(
        final CountDownLatch start, final List<DataIntegrityViolationException> races) {
        try {
            start.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return;
        }
        try {
            pendingBookService.stage(merger.merge(List.of(completeDune())));
        } catch (DataIntegrityViolationException race) {
            races.add(race);
        }
    }

    @Test
    @DisplayName("a candidate with every required field is promoted into book")
    void readyCandidateIsPromoted() {
        pendingBookService.stage(merger.merge(List.of(completeDune())));

        pendingBookService.promoteReady();

        assertThat(books.findByOpenLibraryWorkKey(OL_KEY))
            .as("a complete candidate must land in book")
            .isPresent();
        assertThat(pendingBooks.findByIsbn13(ISBN))
            .get()
            .satisfies(row -> assertThat(row.getStatus()).isEqualTo(STATUS_PROMOTED));
    }

    @Test
    @DisplayName("an incomplete candidate stays staged and never reaches book")
    void incompleteCandidateStaysStaged() {
        pendingBookService.stage(merger.merge(List.of(sparseDune())));

        pendingBookService.promoteReady();

        assertThat(books.count())
            .as("a candidate missing required fields must not be promoted")
            .isZero();
        assertThat(pendingBooks.findByIsbn13(ISBN))
            .get()
            .satisfies(row -> assertThat(row.getStatus()).isEqualTo(STATUS_PENDING));
    }

    @Test
    @DisplayName("promotion takes the series and rating from the Hardcover source the collect fetches")
    void promotionTakesSeriesAndRatingFromHardcover() {
        HARDCOVER_RESPONSE.set(hardcoverDune());
        pendingBookService.stage(merger.merge(List.of(completeDune())));

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
    @DisplayName("re-promotion clears a stale series when the Hardcover source now reports none")
    void rePromotionClearsStaleSeriesWhenSourceHasNone() {
        HARDCOVER_RESPONSE.set(hardcoverDune());
        pendingBookService.stage(merger.merge(List.of(completeDune())));
        pendingBookService.promoteReady();

        HARDCOVER_RESPONSE.set(hardcoverDuneWithoutSeries());
        rePromote();

        assertThat(books.findByOpenLibraryWorkKey(OL_KEY))
            .as("Hardcover resolved and reported no series, so the stale label is cleared")
            .get()
            .satisfies(book -> {
                assertThat(book.getSeriesName()).isNull();
                assertThat(book.getSeriesPosition()).isNull();
            });
    }

    @Test
    @DisplayName("re-promotion keeps a real series when Hardcover fails, even though Wikidata resolves empty")
    void rePromotionKeepsSeriesWhenHardcoverFails() {
        HARDCOVER_RESPONSE.set(hardcoverDune());
        pendingBookService.stage(merger.merge(List.of(completeDune())));
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
        pendingBookService.stage(merger.merge(List.of(duneBy(AUTHOR, STALE_AUTHOR))));
        pendingBookService.promoteReady();

        OPEN_LIBRARY_RESPONSE.set(openLibraryDune());
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
        pendingBookService.stage(merger.merge(List.of(completeDune())));
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

    @Test
    @DisplayName("a candidate colliding with a promoted work is retired and the poll reaches the rest")
    void collidingCandidateIsRetiredAndPollContinues() {
        pendingBookService.stage(merger.merge(List.of(completeDune())));
        pendingBookService.promoteReady();
        pendingBookService.stage(merger.merge(List.of(secondEditionDune())));
        pendingBookService.stage(merger.merge(List.of(duneMessiah())));
        OPEN_LIBRARY_RESPONSE.set(openLibrarySecondEditionResolvingDunesWork());

        pendingBookService.promoteReady();

        assertThat(pendingBooks.findByDedupKey(SECOND_EDITION_ISBN))
            .as("the second edition resolves a work key another row owns; no retry changes that")
            .get()
            .satisfies(row -> assertThat(row.getStatus()).isEqualTo(STATUS_DUPLICATE));
        assertThat(books.findByDedupKey(SEQUEL_ISBN))
            .as("the candidate behind the colliding one must still be promoted")
            .isPresent();
    }

    @Test
    @DisplayName("an attempted candidate is not collected again until the retry window passes")
    void attemptedCandidateWaitsForTheRetryWindow() {
        pendingBookService.stage(merger.merge(List.of(sparseDune())));
        pendingBookService.promoteReady();

        OPEN_LIBRARY_RESPONSE.set(openLibraryCompleteDune());
        pendingBookService.promoteReady();

        assertThat(pendingBooks.findByIsbn13(ISBN))
            .as("the candidate was attempted moments ago, so the poll must skip it this cycle")
            .get()
            .satisfies(row -> assertThat(row.getStatus()).isEqualTo(STATUS_PENDING));
    }

    @Test
    @DisplayName("re-staging a retired candidate revives it for promotion")
    void restagingRevivesRetiredCandidate() {
        pendingBookService.stage(merger.merge(List.of(sparseDune())));
        final PendingBook retired = pendingBooks.findByIsbn13(ISBN).orElseThrow();
        retired.setStatus(STATUS_INCOMPLETE_FINAL);
        pendingBooks.save(retired);

        pendingBookService.stage(merger.merge(List.of(completeDune())));
        pendingBookService.promoteReady();

        assertThat(books.findByOpenLibraryWorkKey(OL_KEY))
            .as("a re-discovered candidate must rejoin the poll and promote")
            .isPresent();
    }

    @Test
    @DisplayName("an upsert without authors keeps the stored authors")
    void upsertWithoutAuthorsKeepsStoredAuthors() {
        catalogService.upsertFromSource(completeDune());

        catalogService.upsertFromSource(sparseDune());

        assertThat(books.findByOpenLibraryWorkKey(OL_KEY))
            .as("null authors mean the source did not carry the field, so the stored set is kept")
            .get()
            .satisfies(book -> assertThat(book.getAuthors())
                .extracting(AUTHOR_NAME_FIELD)
                .containsExactly(AUTHOR));
    }

    @Test
    @DisplayName("an upsert whose author names are all blank keeps the stored authors")
    void upsertWithBlankAuthorNamesKeepsStoredAuthors() {
        catalogService.upsertFromSource(completeDune());

        catalogService.upsertFromSource(duneBy(" "));

        assertThat(books.findByOpenLibraryWorkKey(OL_KEY))
            .as("a response with no usable author name must not strip the book's authors")
            .get()
            .satisfies(book -> assertThat(book.getAuthors())
                .extracting(AUTHOR_NAME_FIELD)
                .containsExactly(AUTHOR));
    }

    @Test
    @DisplayName("a two-author book comes back with each subject once")
    void fetchReturnsEachSubjectOnceForTwoAuthorBook() {
        pendingBookService.stage(merger.merge(List.of(duneBy(AUTHOR, STALE_AUTHOR))));
        pendingBookService.promoteReady();

        assertThat(books.findByDedupKey(ISBN))
            .as("the read fetch joins authors and subjects; the join must not repeat subject rows")
            .get()
            .satisfies(book -> assertThat(book.getSubjects())
                .extracting("subject")
                .containsExactly(GENRE));
    }

    /**
     * Re-promotes the staged book the way the nightly refresh does: rebuild the seed from the
     * pending row, collect the sources for it, then promote at once. The staging poll only promotes
     * PENDING rows, so a book already promoted is re-promoted through this path, not
     * {@link PendingBookService#promoteReady}.
     */
    private void rePromote() {
        final PendingBook row = pendingBooks.findByIsbn13(ISBN).orElseThrow();
        final MergedBook collected = sourceCollector.collectFor(pendingBookMapper.toSourceBook(row));
        pendingBookService.promoteNow(ISBN, collected);
    }

    private static SourceBook openLibraryDune() {
        return SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
            .isbn13(ISBN)
            .title(TITLE)
            .authors(SourceAuthor.ofNames(List.of(AUTHOR)))
            .build();
    }

    private static SourceBook openLibraryCompleteDune() {
        return SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
            .isbn13(ISBN)
            .title(TITLE)
            .authors(SourceAuthor.ofNames(List.of(AUTHOR)))
            .coverUrl(COVER_URL)
            .description(DESCRIPTION)
            .publicationYear(YEAR)
            .build();
    }

    private static SourceBook secondEditionDune() {
        return SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
            .isbn13(SECOND_EDITION_ISBN)
            .title(TITLE)
            .authors(SourceAuthor.ofNames(List.of(AUTHOR)))
            .coverUrl("https://covers.openlibrary.org/b/id/2-L.jpg")
            .description(DESCRIPTION)
            .publicationYear(YEAR)
            .build();
    }

    private static SourceBook openLibrarySecondEditionResolvingDunesWork() {
        return SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
            .isbn13(SECOND_EDITION_ISBN)
            .openLibraryWorkKey(OL_KEY)
            .title(TITLE)
            .authors(SourceAuthor.ofNames(List.of(AUTHOR)))
            .build();
    }

    private static SourceBook duneMessiah() {
        return SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
            .isbn13(SEQUEL_ISBN)
            .title(SEQUEL_TITLE)
            .authors(SourceAuthor.ofNames(List.of(AUTHOR)))
            .coverUrl("https://covers.openlibrary.org/b/id/3-L.jpg")
            .description("Twelve years after his victory, Paul Atreides rules as emperor of the known universe.")
            .publicationYear(YEAR)
            .rawSubjects(List.of(GENRE))
            .build();
    }

    private static SourceBook hardcoverDune() {
        return SourceBook.builder(BookFieldSource.HARDCOVER)
            .isbn13(ISBN)
            .title(TITLE)
            .seriesName(TITLE)
            .seriesPosition(SERIES_POSITION)
            .averageRating(RATING)
            .build();
    }

    private static SourceBook hardcoverDuneWithoutSeries() {
        return SourceBook.builder(BookFieldSource.HARDCOVER)
            .isbn13(ISBN)
            .title(TITLE)
            .averageRating(RATING)
            .build();
    }

    private static SourceBook completeDune() {
        return duneBy(AUTHOR);
    }

    private static SourceBook duneBy(final String... authorNames) {
        return SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
            .isbn13(ISBN)
            .openLibraryWorkKey(OL_KEY)
            .title(TITLE)
            .authors(SourceAuthor.ofNames(List.of(authorNames)))
            .coverUrl(COVER_URL)
            .description(DESCRIPTION)
            .publicationYear(YEAR)
            .rawSubjects(List.of(GENRE))
            .build();
    }

    private static SourceBook sparseDune() {
        return SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
            .isbn13(ISBN)
            .openLibraryWorkKey(OL_KEY)
            .title(TITLE)
            .build();
    }

    /**
     * Drives promotion with controllable Hardcover and OpenLibrary stubs instead of live network
     * calls. A set response is returned by ISBN, so the source resolves; a null response throws, so
     * the source does not resolve and the collect models a transient outage.
     */
    @TestConfiguration
    static class NoNetworkSources {

        @Bean
        @Primary
        SourceCollector noNetworkSourceCollector(final SourceMerger merger) {
            return new SourceCollector(
                merger,
                List.of(
                    new ControllableClient(BookFieldSource.HARDCOVER, HARDCOVER_RESPONSE),
                    new ControllableClient(BookFieldSource.OPEN_LIBRARY, OPEN_LIBRARY_RESPONSE),
                    new EmptyWikidataClient()),
                new DescriptionSelector(List.of()), Runnable::run);
        }
    }

    private static final class ControllableClient implements BookSourceClient {

        private final BookFieldSource reportedSource;

        private final AtomicReference<@Nullable SourceBook> response;

        ControllableClient(
            final BookFieldSource reportedSource,
            final AtomicReference<@Nullable SourceBook> response
        ) {
            this.reportedSource = reportedSource;
            this.response = response;
        }

        @Override
        public BookFieldSource source() {
            return reportedSource;
        }

        @Override
        public Optional<SourceBook> fetchByIsbn(final String isbn) {
            final SourceBook book = response.get();
            if (book == null) {
                throw WebClientResponseException.create(
                    HTTP_SERVER_ERROR, "Service Unavailable", HttpHeaders.EMPTY, new byte[0], null);
            }
            return isbn.equals(book.isbn13()) ? Optional.of(book) : Optional.empty();
        }

        @Override
        public Optional<SourceBook> fetchByTitleAuthor(final String title, final String author) {
            return Optional.empty();
        }
    }

    /** Always resolves with no match, modelling the sparse Wikidata fallback that carries no series. */
    private static final class EmptyWikidataClient implements BookSourceClient {

        @Override
        public BookFieldSource source() {
            return BookFieldSource.WIKIDATA;
        }

        @Override
        public Optional<SourceBook> fetchByIsbn(final String isbn) {
            return Optional.empty();
        }

        @Override
        public Optional<SourceBook> fetchByTitleAuthor(final String title, final String author) {
            return Optional.empty();
        }
    }
}
