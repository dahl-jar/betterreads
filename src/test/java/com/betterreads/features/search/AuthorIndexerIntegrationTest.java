package com.betterreads.features.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.betterreads.book.BookPromotedEvent;
import com.betterreads.testsupport.CatalogRows;
import com.betterreads.testsupport.ContainerizedTest;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = "betterreads.catalog.staging.poll-enabled=false")
class AuthorIndexerIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String INDEX = "books-author-indexer-test";

    private static final String ROTHFUSS = "Patrick Rothfuss";

    private static final String SIMONETTI = "Marc Simonetti";

    private static final String ROTHFUSS_QUERY = "rothfuss";

    private static final String AUTHOR_ROLE = "AUTHOR";

    private static final String WIND_KEY = "9780756404741";

    private static final int PAGE = 20;

    @Autowired
    private AuthorIndexer indexer;

    @Autowired
    private AuthorSearchService searchService;

    @Autowired
    @Qualifier("searchCacheManager")
    private CacheManager searchCacheManager;

    @Autowired
    private Client client;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private ApplicationEventPublisher events;

    @DynamicPropertySource
    static void meilisearch(final DynamicPropertyRegistry registry) {
        MeilisearchServer.register(registry, INDEX);
    }

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM book");
        jdbc.update("DELETE FROM author");
        final Index authors = client.index(MeilisearchServer.authorsIndex(INDEX));
        authors.waitForTask(authors.deleteAllDocuments().getTaskUid());
        searchCacheManager.getCache(SearchResultsCache.AUTHORS_NAME).clear();
    }

    @Nested
    class Boot {

        @Test
        void shouldFillEmptyIndexOnBoot() {
            nameOfTheWind();

            indexer.run(new DefaultApplicationArguments());

            assertThat(names(ROTHFUSS_QUERY)).containsExactly(ROTHFUSS);
        }

        @Test
        void shouldSkipContributorOnlyAuthors() {
            nameOfTheWind();

            indexer.run(new DefaultApplicationArguments());

            assertThat(names("simonetti")).isEmpty();
        }

        @Test
        void shouldLeaveFilledIndexAlone() {
            nameOfTheWind();
            indexer.run(new DefaultApplicationArguments());
            credit(book("9780765326355", "The Way of Kings"), author("Brandon Sanderson"), AUTHOR_ROLE);

            indexer.run(new DefaultApplicationArguments());

            assertThat(names("sanderson")).isEmpty();
        }
    }

    @Nested
    class Updates {

        @Test
        void shouldReindexAuthorsOfPromotedBook() {
            nameOfTheWind();

            transactions.executeWithoutResult(status -> events.publishEvent(new BookPromotedEvent(WIND_KEY)));

            assertThat(names(ROTHFUSS_QUERY)).containsExactly(ROTHFUSS);
        }

        @Test
        void shouldDropAuthorWithoutPrimaryCredit() {
            final long bookId = nameOfTheWind();
            indexer.run(new DefaultApplicationArguments());
            jdbc.update("UPDATE book_author SET role = 'OTHER' WHERE book_id = ?", bookId);

            transactions.executeWithoutResult(status -> events.publishEvent(new BookPromotedEvent(WIND_KEY)));

            assertThat(names(ROTHFUSS_QUERY)).isEmpty();
        }

        @Test
        void shouldIndexChangedAuthorOnReconcile() {
            nameOfTheWind();

            indexer.reconcile();

            assertThat(names(ROTHFUSS_QUERY)).containsExactly(ROTHFUSS);
        }

        @Test
        void shouldRemoveDeletedAuthorFromIndex() {
            nameOfTheWind();
            indexer.run(new DefaultApplicationArguments());
            jdbc.update("DELETE FROM author WHERE name = ?", ROTHFUSS);

            indexer.reconcile();

            assertThat(names(ROTHFUSS_QUERY)).isEmpty();
        }
    }

    private long nameOfTheWind() {
        final long bookId = book(WIND_KEY, "The Name of the Wind");
        credit(bookId, author(ROTHFUSS), AUTHOR_ROLE);
        credit(bookId, author(SIMONETTI), "ILLUSTRATOR");
        return bookId;
    }

    private List<String> names(final String query) {
        return searchService.search(query, 0, PAGE).hits().stream().map(AuthorSearchDocument::name).toList();
    }

    private long book(final String key, final String title) {
        return jdbc.queryForObject(
            "INSERT INTO book (title, dedup_key, rating_count, average_rating) VALUES (?, ?, 1000, 4.5) "
                + "RETURNING book_id", Long.class, title, key);
    }

    private long author(final String name) {
        return CatalogRows.author(jdbc, name);
    }

    private void credit(final long bookId, final long authorId, final String role) {
        jdbc.update("INSERT INTO book_author (book_id, author_id, role, position) "
            + "VALUES (?, ?, ?, (SELECT count(*) FROM book_author WHERE book_id = ?))", bookId, authorId, role, bookId);
    }
}
