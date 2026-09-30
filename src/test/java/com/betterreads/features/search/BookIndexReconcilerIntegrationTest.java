package com.betterreads.features.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.betterreads.book.Author;
import com.betterreads.book.AuthorRepository;
import com.betterreads.book.Book;
import com.betterreads.book.BookRepository;
import com.betterreads.testsupport.Books;
import com.betterreads.testsupport.ContainerizedTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = "betterreads.search.reconcile-page-size=1")
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BookIndexReconcilerIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final int FULL_PAGE = 20;

    private static final int ONE_HIT = 1;

    @Autowired
    private BookIndexReconciler reconciler;

    @Autowired
    private BookSearchService searchService;

    @Autowired
    private BookRepository books;

    @Autowired
    private AuthorRepository authors;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void meilisearchProps(final DynamicPropertyRegistry registry) {
        MeilisearchServer.register(registry, "books-reconcile-test");
    }

    @BeforeEach
    void clearCatalog() {
        books.deleteAll();
        authors.deleteAll();
    }

    @Test
    void shouldIndexEveryPage() {
        saveBook("rc-1", "Dune", "Frank Herbert");
        saveBook("rc-2", "Hyperion", "Dan Simmons");

        reconciler.reconcile();

        final BookSearchResult dune = searchService.search("dune", 0, FULL_PAGE).result();
        final BookSearchResult hyperion = searchService.search("hyperion", 0, FULL_PAGE).result();
        assertThat(dune.hits()).hasSize(ONE_HIT);
        assertThat(hyperion.hits()).hasSize(ONE_HIT);
    }

    @Test
    void shouldSkipBooksNotChangedRecently() {
        final long old = saveBook("rc-3", "Foundation", "Isaac Asimov");
        final long recent = saveBook("rc-4", "Neuromancer", "William Gibson");
        jdbc.update("UPDATE book SET updated_at = now() - interval '2 days 1 hour' WHERE book_id = ?", old);
        jdbc.update("UPDATE book SET updated_at = now() - interval '1 day 23 hours' WHERE book_id = ?", recent);

        reconciler.reconcile();

        final BookSearchResult foundation = searchService.search("foundation", 0, FULL_PAGE).result();
        final BookSearchResult neuromancer = searchService.search("neuromancer", 0, FULL_PAGE).result();
        assertThat(foundation.hits()).isEmpty();
        assertThat(neuromancer.hits()).hasSize(ONE_HIT);
    }

    private long saveBook(final String key, final String title, final String authorName) {
        final Author author = authors.save(Books.author(authorName));
        final Book book = Books.promoted(key, title, author);
        book.setHardcoverId(key);
        return books.save(book).getBookId();
    }
}
