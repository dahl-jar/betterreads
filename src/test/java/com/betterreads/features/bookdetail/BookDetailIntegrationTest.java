package com.betterreads.features.bookdetail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.betterreads.book.Author;
import com.betterreads.book.AuthorRepository;
import com.betterreads.book.Book;
import com.betterreads.book.BookDetailCache;
import com.betterreads.book.BookPromotedEvent;
import com.betterreads.book.BookRepository;
import com.betterreads.pendingbook.PendingBook;
import com.betterreads.pendingbook.PendingBookRepository;
import com.betterreads.testsupport.Books;
import com.betterreads.testsupport.ContainerizedTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Book-detail reads against a real Postgres and Redis. */
@SpringBootTest
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
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

    private static final String TITLE_PATH = "$.data.title";

    private static final String AUTHORS_PATH = "$.data.authors[0]";

    private static final String COMPLETE_PATH = "$.data.complete";

    private static final String COVER_URL = "https://covers.example/kings.jpg";

    private static final String RETITLE_SQL = "UPDATE book SET title = ? WHERE hardcover_id = ?";

    private static final int YEAR = 2010;

    private static final BigDecimal AVERAGE_RATING = new BigDecimal("4.65");

    private static final String COVER_PATH = "/api/v1/images/covers/";

    private static final String EVENTS_PATH = "/api/v1/books/{key}/events";

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

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

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = securedMockMvc();
        pendingBooks.deleteAll();
        books.deleteAll();
        authors.deleteAll();
        cacheManager.getCache(BookDetailCache.NAME).clear();
    }

    @Nested
    @DisplayName("GET /api/v1/books/{key}")
    class GetBook {

        @Test
        @DisplayName("returns the promoted book marked complete")
        void promotedBook() throws Exception {
            saveCompleteBook(HARDCOVER_KEY);

            mockMvc.perform(get(BOOK_PATH, HARDCOVER_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath(TITLE_PATH).value(TITLE))
                .andExpect(jsonPath(AUTHORS_PATH).value(AUTHOR))
                .andExpect(jsonPath("$.data.firstPublishYear").value(Books.PUBLISH_YEAR))
                .andExpect(jsonPath("$.data.coverUrl").value(containsString(COVER_PATH + HARDCOVER_KEY)))
                .andExpect(jsonPath("$.data.averageRating").value(AVERAGE_RATING.doubleValue()))
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
                .andExpect(jsonPath(COMPLETE_PATH).value(false));
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
        @DisplayName("a second read of a promoted book is served from cache")
        void cachesPromotedRead() throws Exception {
            saveCompleteBook(HARDCOVER_KEY);
            mockMvc.perform(get(BOOK_PATH, HARDCOVER_KEY)).andExpect(status().isOk());

            jdbcTemplate.update(RETITLE_SQL,
                "Stale Title Behind The Cache", HARDCOVER_KEY);

            mockMvc.perform(get(BOOK_PATH, HARDCOVER_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath(TITLE_PATH).value(TITLE));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/books/{key}/events")
    class StreamBook {

        @Test
        void shouldPushPromotedBookToOpenStream() throws Exception {
            savePendingSeed();
            final MvcResult stream = mockMvc.perform(get(EVENTS_PATH, PENDING_KEY))
                .andExpect(request().asyncStarted())
                .andReturn();

            transactionTemplate.executeWithoutResult(status -> {
                saveCompleteBook(PENDING_KEY);
                eventPublisher.publishEvent(new BookPromotedEvent(PENDING_KEY));
            });

            assertThat(stream.getResponse().getContentAsString())
                .contains("event:book-updated")
                .contains("\"complete\":true");
        }

        @Test
        void shouldReturn404WhenKeyIsUnknown() throws Exception {
            mockMvc.perform(get(EVENTS_PATH, UNKNOWN_KEY))
                .andExpect(status().isNotFound());
        }
    }

    private void saveCompleteBook(final String key) {
        final Author author = authors.save(Books.author(AUTHOR));
        final Book book = Books.promoted(key, TITLE, author);
        book.setHardcoverId(key);
        book.setIsbn("9780765326355");
        book.setAverageRating(AVERAGE_RATING);
        book.setRatingCount(RATING_COUNT);
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
