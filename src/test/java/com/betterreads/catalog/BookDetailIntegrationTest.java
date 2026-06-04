package com.betterreads.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.betterreads.catalog.entity.Author;
import com.betterreads.catalog.entity.Book;
import com.betterreads.catalog.entity.PendingBook;
import com.betterreads.catalog.repository.AuthorRepository;
import com.betterreads.catalog.repository.BookRepository;
import com.betterreads.catalog.repository.PendingBookRepository;
import com.betterreads.support.ContainerizedTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Book-detail reads against a real Postgres and Redis: a promoted book serves its full detail and
 * is marked complete; a key that only exists in {@code pending_book} serves the seed and is marked
 * incomplete; an unknown key is a 404. The endpoint is public, the promoted read is cached, and a
 * re-promotion evicts the stale entry.
 */
@SpringBootTest
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
    "jwt.secret=integration-test-secret-must-be-at-least-256-bits-long-padding-padding",
    "jwt.issuer=betterreads-it",
    "betterreads.catalog.staging.poll-enabled=false"
})
class BookDetailIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String HARDCOVER_KEY = "hc-42";

    private static final String PENDING_KEY = "9780000000001";

    private static final String UNKNOWN_KEY = "no-such-key";

    private static final String TITLE = "The Way of Kings";

    private static final String AUTHOR = "Brandon Sanderson";

    private static final int RATING_COUNT = 120_000;

    private static final String BOOK_PATH = "/api/v1/books/{key}";

    private static final String TITLE_PATH = "$.title";

    private static final String AUTHORS_PATH = "$.authors[0]";

    private static final String COMPLETE_PATH = "$.complete";

    private static final String DETAIL_CACHE = "bookDetails";

    private static final String COVER_URL = "https://covers.example/kings.jpg";

    private static final String RETITLE_SQL = "UPDATE book SET title = ? WHERE hardcover_id = ?";

    private static final String SECOND_EDITION = "Second Edition";

    private static final int YEAR = 2010;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private BookRepository books;

    @Autowired
    private AuthorRepository authors;

    @Autowired
    private PendingBookRepository pendingBooks;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .addFilter(springSecurityFilterChain)
            .build();
        pendingBooks.deleteAll();
        books.deleteAll();
        authors.deleteAll();
        cacheManager.getCache(DETAIL_CACHE).clear();
    }

    @Nested
    @DisplayName("GET /api/v1/books/{key}")
    class GetBook {

        @Test
        @DisplayName("returns the promoted book marked complete")
        void promotedBook() throws Exception {
            saveCompleteBook();

            mockMvc.perform(get(BOOK_PATH, HARDCOVER_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath(TITLE_PATH).value(TITLE))
                .andExpect(jsonPath(AUTHORS_PATH).value(AUTHOR))
                .andExpect(jsonPath("$.firstPublishYear").value(YEAR))
                .andExpect(jsonPath(COMPLETE_PATH).value(true));
        }

        @Test
        @DisplayName("falls back to the pending seed marked incomplete")
        void pendingSeed() throws Exception {
            savePendingSeed();

            mockMvc.perform(get(BOOK_PATH, PENDING_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath(TITLE_PATH).value(TITLE))
                .andExpect(jsonPath(AUTHORS_PATH).value(AUTHOR))
                .andExpect(jsonPath(COMPLETE_PATH).value(false))
                .andExpect(jsonPath("$.description").doesNotExist());
        }

        @Test
        @DisplayName("returns 404 when neither table has the key")
        void unknownKey() throws Exception {
            mockMvc.perform(get(BOOK_PATH, UNKNOWN_KEY))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("bookDetails cache")
    class Caching {

        @Test
        @DisplayName("a second read of a promoted book is served from cache, not the database")
        void cachesPromotedRead() throws Exception {
            saveCompleteBook();
            mockMvc.perform(get(BOOK_PATH, HARDCOVER_KEY)).andExpect(status().isOk());

            jdbcTemplate.update(RETITLE_SQL,
                "Stale Title Behind The Cache", HARDCOVER_KEY);

            mockMvc.perform(get(BOOK_PATH, HARDCOVER_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath(TITLE_PATH).value(TITLE));
        }

        @Test
        @DisplayName("evicting the entry serves the fresh row")
        void evictServesFresh() throws Exception {
            saveCompleteBook();
            mockMvc.perform(get(BOOK_PATH, HARDCOVER_KEY)).andExpect(status().isOk());

            jdbcTemplate.update(RETITLE_SQL, SECOND_EDITION, HARDCOVER_KEY);
            cacheManager.getCache(DETAIL_CACHE).evict(HARDCOVER_KEY);

            mockMvc.perform(get(BOOK_PATH, HARDCOVER_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath(TITLE_PATH).value(SECOND_EDITION));
        }
    }

    private void saveCompleteBook() {
        final Author author = new Author();
        author.setName(AUTHOR);
        final Author savedAuthor = authors.save(author);
        final Book book = new Book();
        book.setDedupKey(HARDCOVER_KEY);
        book.setHardcoverId(HARDCOVER_KEY);
        book.setTitle(TITLE);
        book.setDescription("A long epic fantasy opening the Stormlight Archive.");
        book.setCoverUrl(COVER_URL);
        book.setFirstPublishYear(YEAR);
        book.setIsbn("9780765326355");
        book.setAverageRating(new BigDecimal("4.65"));
        book.setRatingCount(RATING_COUNT);
        book.setAuthors(Set.of(savedAuthor));
        books.save(book);
    }

    private void savePendingSeed() {
        final PendingBook seed = new PendingBook();
        seed.setDedupKey(PENDING_KEY);
        seed.setIsbn13(PENDING_KEY);
        seed.setTitle(TITLE);
        seed.setAuthors(AUTHOR);
        seed.setCoverUrl(COVER_URL);
        seed.setFirstPublishYear(YEAR);
        pendingBooks.save(seed);
    }
}
