package com.betterreads.catalog.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.betterreads.catalog.repository.BookRepository;
import com.betterreads.catalog.repository.PendingBookRepository;
import com.betterreads.catalog.service.source.BookFieldSource;
import com.betterreads.catalog.service.pipeline.PendingBookService;
import com.betterreads.catalog.service.source.SourceAuthor;
import com.betterreads.catalog.service.source.SourceBook;
import com.betterreads.catalog.service.pipeline.SourceCollector;
import com.betterreads.catalog.service.source.SourceMerger;
import com.betterreads.search.dto.BookSearchResult;
import com.betterreads.search.service.BookSearchService;
import com.betterreads.support.ContainerizedTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * A promoted book becomes searchable without waiting for the nightly reconcile, driven by the
 * after-commit index hook on promotion.
 */
@SpringBootTest
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
    "jwt.secret=integration-test-secret-must-be-at-least-256-bits-long-padding-padding",
    "jwt.issuer=betterreads-it",
    "betterreads.catalog.staging.poll-enabled=false"
})
@Import(BookPromotionIndexingIntegrationTest.NoNetworkSources.class)
class BookPromotionIndexingIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String TEST_MASTER_KEY = "testMasterKey1234567890";

    private static final int MEILISEARCH_PORT = 7700;

    private static final String ISBN = "9780441013593";

    private static final int DUNE_YEAR = 1965;

    private static final int FULL_PAGE = 20;

    private static final int INDEX_WAIT_SECONDS = 10;

    private static final int ONE_HIT = 1;

    static final GenericContainer<?> MEILISEARCH = new GenericContainer<>(
            DockerImageName.parse("getmeili/meilisearch:v1.11"))
        .withExposedPorts(MEILISEARCH_PORT)
        .withEnv("MEILI_MASTER_KEY", TEST_MASTER_KEY)
        .withEnv("MEILI_NO_ANALYTICS", "true")
        .withEnv("MEILI_ENV", "development");

    static {
        MEILISEARCH.start();
    }

    @Autowired
    private SourceMerger merger;

    @Autowired
    private PendingBookService pendingBookService;

    @Autowired
    private BookSearchService searchService;

    @Autowired
    private BookRepository books;

    @Autowired
    private PendingBookRepository pendingBooks;

    @DynamicPropertySource
    static void meilisearchProps(final DynamicPropertyRegistry registry) {
        registry.add("meilisearch.host",
            () -> "http://" + MEILISEARCH.getHost() + ":" + MEILISEARCH.getMappedPort(MEILISEARCH_PORT));
        registry.add("meilisearch.master-key", () -> TEST_MASTER_KEY);
        registry.add("meilisearch.index-name", () -> "books-promote-test");
    }

    @BeforeEach
    void clearCatalog() {
        pendingBooks.deleteAll();
        books.deleteAll();
    }

    @Test
    @DisplayName("a promoted book is searchable right after promotion")
    void promotedBookIsSearchable() {
        pendingBookService.stage(merger.merge(List.of(completeDune())));

        pendingBookService.promoteReady();

        await().atMost(Duration.ofSeconds(INDEX_WAIT_SECONDS)).untilAsserted(() -> {
            final BookSearchResult result = searchService.search("dune", 0, FULL_PAGE);
            assertThat(result.hits()).hasSize(ONE_HIT);
        });
    }

    private static SourceBook completeDune() {
        return SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
            .isbn13(ISBN)
            .openLibraryWorkKey("OL893415W")
            .title("Dune")
            .description("Paul Atreides leads the Fremen against the Padishah Empire on Arrakis.")
            .coverUrl("https://covers.example/dune.jpg")
            .publicationYear(DUNE_YEAR)
            .authors(List.of(SourceAuthor.ofName("Frank Herbert")))
            .build();
    }

    /** Replaces the source collector with one that re-merges only the staged data, no network. */
    @TestConfiguration
    static class NoNetworkSources {

        @Bean
        @Primary
        SourceCollector noNetworkSourceCollector(final SourceMerger merger) {
            return new SourceCollector(merger, List.of(), Runnable::run);
        }
    }
}
