package com.betterreads.catalog.pipeline;

import com.betterreads.support.ContainerizedTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.betterreads.catalog.repository.BookRepository;
import com.betterreads.catalog.repository.PendingBookRepository;
import com.betterreads.catalog.service.source.BookFieldSource;
import com.betterreads.catalog.service.source.MergedBook;
import com.betterreads.catalog.service.pipeline.DescriptionSelector;
import com.betterreads.catalog.service.pipeline.PendingBookService;
import com.betterreads.catalog.service.source.SourceAuthor;
import com.betterreads.catalog.service.source.SourceBook;
import com.betterreads.catalog.service.pipeline.SourceCollector;
import com.betterreads.catalog.service.source.SourceMerger;
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

    private static final String STATUS_PENDING = "PENDING";

    private static final String STATUS_PROMOTED = "PROMOTED";

    private static final int STAGING_THREADS = 4;

    @Autowired
    private PendingBookService pendingBookService;

    @Autowired
    private SourceMerger merger;

    @Autowired
    private PendingBookRepository pendingBooks;

    @Autowired
    private BookRepository books;

    @BeforeEach
    void clearCatalog() {
        pendingBooks.deleteAll();
        books.deleteAll();
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
    @DisplayName("promotion keeps the Hardcover series and rating the candidate was staged with")
    void promotionKeepsSeriesAndRating() {
        pendingBookService.stage(merger.merge(List.of(completeDune(), hardcoverDune())));

        pendingBookService.promoteReady();

        assertThat(books.findByOpenLibraryWorkKey(OL_KEY))
            .as("the staged series and rating must survive promotion into book")
            .get()
            .satisfies(book -> {
                assertThat(book.getSeriesName()).isEqualTo(TITLE);
                assertThat(book.getSeriesPosition()).isEqualTo(SERIES_POSITION);
                assertThat(book.getAverageRating()).isNotNull();
            });
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

    private static SourceBook completeDune() {
        return SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
            .isbn13(ISBN)
            .openLibraryWorkKey(OL_KEY)
            .title(TITLE)
            .authors(SourceAuthor.ofNames(List.of("Frank Herbert")))
            .coverUrl("https://covers.openlibrary.org/b/id/1-L.jpg")
            .description("A desert planet holds the universe's only source of the spice melange.")
            .publicationYear(YEAR)
            .rawSubjects(List.of("science fiction"))
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
     * Replaces the live source clients with one that returns nothing, so promotion is driven only by
     * the staged data and the test makes no network calls.
     */
    @TestConfiguration
    static class NoNetworkSources {

        @Bean
        @Primary
        SourceCollector noNetworkSourceCollector(final SourceMerger merger) {
            return new SourceCollector(merger, List.of(), new DescriptionSelector(List.of()), Runnable::run);
        }
    }
}
