package com.betterreads.features.bookstaging;

import com.betterreads.book.BookRepository;
import com.betterreads.bookmerge.SourceMerger;
import com.betterreads.booksource.MergedBook;
import com.betterreads.pendingbook.PendingBook;
import com.betterreads.pendingbook.PendingBookRepository;
import com.betterreads.pendingbook.PendingBookStatus;
import com.betterreads.testsupport.ContainerizedTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static com.betterreads.features.bookstaging.DuneBooks.ISBN;
import static com.betterreads.features.bookstaging.DuneBooks.OL_KEY;
import static com.betterreads.features.bookstaging.DuneBooks.TITLE;
import static com.betterreads.features.bookstaging.DuneBooks.completeDune;
import static com.betterreads.features.bookstaging.DuneBooks.sparseDune;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "betterreads.catalog.staging.poll-enabled=false"
})
@Import(NoNetworkSources.class)
class PendingBookStagingIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

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
        NoNetworkSources.reset();
    }

    @Test
    @DisplayName("staging a merged book writes one pending row")
    void stagingWritesPendingRow() {
        final MergedBook merged = merger.merge(null, List.of(completeDune()));

        pendingBookService.stage(merged);

        assertThat(pendingBooks.findByIsbn13(ISBN))
            .get()
            .satisfies(row -> {
                assertThat(row.getTitle()).isEqualTo(TITLE);
                assertThat(row.getStatus()).isEqualTo(PendingBookStatus.PENDING);
            });
    }

    @Test
    @DisplayName("staging the same book twice updates the row rather than inserting a second")
    void stagingTwiceUpdatesInPlace() {
        pendingBookService.stage(merger.merge(null, List.of(sparseDune())));

        pendingBookService.stage(merger.merge(null, List.of(completeDune())));

        assertThat(pendingBooks.count())
            .as("the second stage of the same ISBN must reuse the existing row")
            .isEqualTo(1L);
        assertThat(pendingBooks.findByIsbn13(ISBN))
            .get()
            .satisfies(row -> assertThat(row.getCoverUrl()).isNotNull());
    }

    // PMD.DoNotUseThreads: the test needs real threads to force the race
    @SuppressWarnings("PMD.DoNotUseThreads")
    @Test
    @DisplayName("concurrent staging of the same new book ends with one row, no error escapes")
    void concurrentStagingOfSameBookKeepsOneRow() {
        final CountDownLatch start = new CountDownLatch(1);
        final List<DataIntegrityViolationException> races = new CopyOnWriteArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(STAGING_THREADS)) {
            for (int i = 0; i < STAGING_THREADS; i++) {
                pool.execute(() -> stageOnSignal(start, races));
            }
            start.countDown();
        }

        assertThat(races)
            .as("the staging reserve must absorb the race, not surface a duplicate-key error")
            .isEmpty();
        assertThat(pendingBooks.count())
            .as("the dedup key must collapse the concurrent stages to one row")
            .isEqualTo(1L);
    }

    // PMD.DoNotUseThreads: restoring the interrupt flag inside the race test's pool task
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
            pendingBookService.stage(merger.merge(null, List.of(completeDune())));
        } catch (DataIntegrityViolationException race) {
            races.add(race);
        }
    }

    @Test
    @DisplayName("re-staging a retired candidate revives it for promotion")
    void restagingRevivesRetiredCandidate() {
        pendingBookService.stage(merger.merge(null, List.of(sparseDune())));
        final PendingBook retired = pendingBooks.findByIsbn13(ISBN).orElseThrow();
        retired.setStatus(PendingBookStatus.INCOMPLETE_FINAL);
        pendingBooks.save(retired);

        pendingBookService.stage(merger.merge(null, List.of(completeDune())));
        pendingBookService.promoteReady();

        assertThat(books.findByOpenLibraryWorkKey(OL_KEY))
            .as("a re-discovered candidate must rejoin the poll and promote")
            .isPresent();
    }
}
