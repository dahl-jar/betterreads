package com.betterreads.catalog;

import com.betterreads.support.ContainerizedTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.betterreads.catalog.repository.BookRepository;
import com.betterreads.catalog.repository.PendingBookRepository;
import com.betterreads.catalog.service.BookSourceClient;
import com.betterreads.catalog.service.MergedBook;
import com.betterreads.catalog.service.PendingBookService;
import com.betterreads.catalog.service.SourceBook;
import com.betterreads.catalog.service.SourceMerger;
import com.betterreads.common.util.LogSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Drives the whole catalog pipeline for the six-book slate against the live source APIs and a real
 * Postgres: fetch every source, merge, stage, promote, then read back from {@code book}. Confirms a
 * complete book becomes visible and that the read after promotion is fast.
 *
 * <p>Skipped unless {@code GOOGLE_BOOKS_API_KEY} is in the environment, matching the other live
 * suites, since the merge quality depends on the keyed Google source.
 */
@SpringBootTest(properties = {
    "jwt.secret=integration-test-secret-must-be-at-least-256-bits-long-padding-padding",
    "jwt.issuer=betterreads-e2e",
    "betterreads.catalog.staging.poll-enabled=false"
})
@Testcontainers
@EnabledIfEnvironmentVariable(named = "GOOGLE_BOOKS_API_KEY", matches = ".+")
class CatalogPipelineLiveE2eTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final Logger LOG = LoggerFactory.getLogger(CatalogPipelineLiveE2eTest.class);

    private static final Duration MAX_READ = Duration.ofMillis(200);

    private static final List<Slate> SLATE = List.of(
        new Slate("The Eye of the World", "Robert Jordan"),
        new Slate("A Clash of Kings", "George R. R. Martin"),
        new Slate("The Hobbit", "J.R.R. Tolkien"),
        new Slate("Dune", "Frank Herbert"),
        new Slate("The Sandman", "Neil Gaiman"),
        new Slate("Watchmen", "Alan Moore"));

    @Autowired
    private List<BookSourceClient> sourceClients;

    @Autowired
    private SourceMerger merger;

    @Autowired
    private PendingBookService pendingBookService;

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
    @DisplayName("the slate flows from live sources through staging into book, and reads back fast")
    void slateFlowsEndToEnd() {
        for (final Slate slate : SLATE) {
            stageFromLiveSources(slate);
        }

        pendingBookService.promoteReady();

        final long promoted = books.count();
        LOG.info("catalog.e2e promoted {} of {} slate books into book", promoted, SLATE.size());
        assertThat(promoted)
            .as("the slate's complete books must reach the catalog after one promotion pass")
            .isGreaterThanOrEqualTo(1L);

        assertReadIsFast();
    }

    private void stageFromLiveSources(final Slate slate) {
        final List<SourceBook> sources = new ArrayList<>();
        for (final BookSourceClient client : sourceClients) {
            client.fetchByTitleAuthor(slate.title(), slate.author()).ifPresent(sources::add);
        }
        if (sources.isEmpty()) {
            LOG.warn("catalog.e2e no source returned {}", LogSanitizer.forLog(slate.title()));
            return;
        }
        final MergedBook merged = merger.merge(sources);
        pendingBookService.stage(merged);
        LOG.info("catalog.e2e staged {} from {} sources",
            LogSanitizer.forLog(slate.title()), sources.size());
    }

    private void assertReadIsFast() {
        final long startNanos = System.nanoTime();
        final long count = books.count();
        final Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
        LOG.info("catalog.e2e read {} books in {} ms", count, elapsed.toMillis());
        assertThat(elapsed)
            .as("a catalog read after promotion must be a fast local query, not an external round-trip")
            .isLessThan(MAX_READ);
    }

    private record Slate(String title, String author) {
    }
}
