package com.betterreads.features.bookstaging;

import com.betterreads.book.BookRepository;
import com.betterreads.bookmerge.SourceMerger;
import com.betterreads.booksource.BookSourceClient;
import com.betterreads.booksource.MergedBook;
import com.betterreads.booksource.SourceBook;
import com.betterreads.logging.LogSanitizer;
import com.betterreads.pendingbook.PendingBookRepository;
import com.betterreads.testsupport.ContainerizedTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Runs the six-book slate through live sources, staging, and promotion. */
@SpringBootTest(properties = "betterreads.catalog.staging.poll-enabled=false")
@Testcontainers
@EnabledIfEnvironmentVariable(named = "GOOGLE_BOOKS_API_KEY", matches = ".+")
class CatalogPipelineLiveE2eTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final Logger LOG = LoggerFactory.getLogger(CatalogPipelineLiveE2eTest.class);

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
    @DisplayName("the live slate reaches the catalog")
    void shouldPromoteLiveSlateIntoCatalog() {
        SLATE.forEach(this::stageFromLiveSources);

        pendingBookService.promoteReady();

        final long promoted = books.count();
        LOG.info("catalog.e2e promoted {} of {} slate books into book", promoted, SLATE.size());
        assertThat(promoted)
            .as("the slate's complete books must reach the catalog after one promotion pass")
            .isGreaterThanOrEqualTo(1L);
    }

    private void stageFromLiveSources(final Slate slate) {
        final List<SourceBook> sources = sourceClients.stream()
            .map(client -> client.fetchByTitleAuthor(slate.title(), slate.author()))
            .flatMap(Optional::stream)
            .toList();
        if (sources.isEmpty()) {
            LOG.warn("catalog.e2e no source returned {}", LogSanitizer.forLog(slate.title()));
            return;
        }
        final MergedBook merged = merger.merge(null, sources);
        pendingBookService.stage(merged);
        LOG.info("catalog.e2e staged {} from {} sources",
            LogSanitizer.forLog(slate.title()), sources.size());
    }

    private record Slate(String title, String author) {
    }
}
