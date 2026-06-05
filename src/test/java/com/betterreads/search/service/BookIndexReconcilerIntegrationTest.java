package com.betterreads.search.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.betterreads.catalog.entity.Author;
import com.betterreads.catalog.entity.Book;
import com.betterreads.catalog.repository.AuthorRepository;
import com.betterreads.catalog.repository.BookRepository;
import com.betterreads.search.dto.BookSearchResult;
import com.betterreads.support.ContainerizedTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * The reconciler walks the catalog and makes every promoted book searchable, so a book present in
 * Postgres but missing from the index is indexed on the next run.
 */
@SpringBootTest
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
    "jwt.secret=integration-test-secret-must-be-at-least-256-bits-long-padding-padding",
    "jwt.issuer=betterreads-it"
})
class BookIndexReconcilerIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String TEST_MASTER_KEY = "testMasterKey1234567890";

    private static final int MEILISEARCH_PORT = 7700;

    private static final int FULL_PAGE = 20;

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
    private BookIndexReconciler reconciler;

    @Autowired
    private BookSearchService searchService;

    @Autowired
    private BookRepository books;

    @Autowired
    private AuthorRepository authors;

    @DynamicPropertySource
    static void meilisearchProps(final DynamicPropertyRegistry registry) {
        registry.add("meilisearch.host",
            () -> "http://" + MEILISEARCH.getHost() + ":" + MEILISEARCH.getMappedPort(MEILISEARCH_PORT));
        registry.add("meilisearch.master-key", () -> TEST_MASTER_KEY);
        registry.add("meilisearch.index-name", () -> "books-reconcile-test");
    }

    @BeforeEach
    void clearCatalog() {
        books.deleteAll();
        authors.deleteAll();
    }

    @Test
    @DisplayName("indexes every catalog book so it is searchable")
    void indexesAllBooks() {
        saveBook("rc-1", "Dune", "Frank Herbert");
        saveBook("rc-2", "Hyperion", "Dan Simmons");

        reconciler.reconcile();

        final BookSearchResult dune = searchService.search("dune", 0, FULL_PAGE);
        final BookSearchResult hyperion = searchService.search("hyperion", 0, FULL_PAGE);
        assertThat(dune.hits()).hasSize(ONE_HIT);
        assertThat(hyperion.hits()).hasSize(ONE_HIT);
    }

    private void saveBook(final String key, final String title, final String authorName) {
        final Author author = new Author();
        author.setName(authorName);
        final Author saved = authors.save(author);
        final Book book = new Book();
        book.setDedupKey(key);
        book.setHardcoverId(key);
        book.setTitle(title);
        book.setAuthors(Set.of(saved));
        books.save(book);
    }
}
