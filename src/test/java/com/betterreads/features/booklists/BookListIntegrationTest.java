package com.betterreads.features.booklists;

import static com.betterreads.testsupport.Books.DUNE_TITLE;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.betterreads.book.Author;
import com.betterreads.book.AuthorRepository;
import com.betterreads.book.Book;
import com.betterreads.book.BookRepository;
import com.betterreads.testsupport.Books;
import com.betterreads.testsupport.ContainerizedTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Homepage book lists and the catalog count against a real Postgres. */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "betterreads.catalog.staging.poll-enabled=false"
})
class BookListIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String LIST_PATH = "/api/v1/books";

    private static final String LIST_PARAM = "list";

    private static final String LIMIT_PARAM = "limit";

    private static final String RECENTLY_ADDED = "RECENTLY_ADDED";

    private static final String TOP_RATED = "TOP_RATED";

    private static final String FIRST_KEY = "$.data[0].key";

    private static final String SECOND_KEY = "$.data[1].key";

    private static final String FIRST_TITLE = "$.data[0].title";

    private static final String DATA_LENGTH = "$.data.length()";

    private static final int WELL_RATED_COUNT = 5000;

    private static final int BARELY_RATED_COUNT = 1000;

    private static final BigDecimal DUNE_AVERAGE = new BigDecimal("4.25");

    private static final BigDecimal FOUR_POINT_ZERO = new BigDecimal("4.00");

    private static final BigDecimal FOUR_POINT_THREE = new BigDecimal("4.30");

    @Autowired
    private BookRepository books;

    @Autowired
    private AuthorRepository authors;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = securedMockMvc();
        books.deleteAll();
        authors.deleteAll();
    }

    @Nested
    @DisplayName("GET /api/v1/books?list=RECENTLY_ADDED")
    class RecentlyAdded {

        @Test
        void ordersByDateAddedNewestFirst() throws Exception {
            final String older = "older";
            final String newer = "newer";
            saveBook(older, "Older Book", FOUR_POINT_ZERO, WELL_RATED_COUNT);
            saveBook(newer, "Newer Book", FOUR_POINT_ZERO, WELL_RATED_COUNT);
            setCreatedAt(older, "2026-01-01T00:00:00Z");
            setCreatedAt(newer, "2026-06-01T00:00:00Z");

            mockMvc.perform(get(LIST_PATH).param(LIST_PARAM, RECENTLY_ADDED))
                .andExpect(status().isOk())
                .andExpect(jsonPath(FIRST_KEY).value(newer))
                .andExpect(jsonPath(SECOND_KEY).value(older));
        }

        @Test
        void includesEveryBookRegardlessOfRatingCount() throws Exception {
            final String unrated = "unrated";
            saveBook(unrated, "Brand New", null, 0);

            mockMvc.perform(get(LIST_PATH).param(LIST_PARAM, RECENTLY_ADDED))
                .andExpect(status().isOk())
                .andExpect(jsonPath(DATA_LENGTH).value(1))
                .andExpect(jsonPath(FIRST_KEY).value(unrated));
        }

        @Test
        void carriesTitleKeyAndSourceRating() throws Exception {
            final String dune = "dune";
            saveBook(dune, DUNE_TITLE, DUNE_AVERAGE, WELL_RATED_COUNT);

            mockMvc.perform(get(LIST_PATH).param(LIST_PARAM, RECENTLY_ADDED))
                .andExpect(jsonPath(FIRST_KEY).value(dune))
                .andExpect(jsonPath(FIRST_TITLE).value(DUNE_TITLE))
                .andExpect(jsonPath("$.data[0].coverUrl").value(containsString("/api/v1/images/covers/" + dune)))
                .andExpect(jsonPath("$.data[0].firstPublishYear").value(Books.PUBLISH_YEAR))
                .andExpect(jsonPath("$.data[0].authors[0]").value("Author of dune"))
                .andExpect(jsonPath("$.data[0].averageRating").value(DUNE_AVERAGE.doubleValue()))
                .andExpect(jsonPath("$.data[0].ratingCount").value(WELL_RATED_COUNT));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/books?list=TOP_RATED")
    class TopRated {

        @Test
        void ordersByAverageRatingDescending() throws Exception {
            final String good = "good";
            final String great = "great";
            saveBook(good, "Good", new BigDecimal("4.10"), WELL_RATED_COUNT);
            saveBook(great, "Great", new BigDecimal("4.80"), WELL_RATED_COUNT);

            mockMvc.perform(get(LIST_PATH).param(LIST_PARAM, TOP_RATED))
                .andExpect(status().isOk())
                .andExpect(jsonPath(FIRST_KEY).value(great))
                .andExpect(jsonPath(SECOND_KEY).value(good));
        }

        @Test
        void excludesBooksAtOrBelowTheRatingFloor() throws Exception {
            final String classic = "classic";
            saveBook("fluke", "Fluke Five Star", new BigDecimal("5.00"), BARELY_RATED_COUNT);
            saveBook(classic, "Beloved Classic", FOUR_POINT_THREE, WELL_RATED_COUNT);

            mockMvc.perform(get(LIST_PATH).param(LIST_PARAM, TOP_RATED))
                .andExpect(status().isOk())
                .andExpect(jsonPath(DATA_LENGTH).value(1))
                .andExpect(jsonPath(FIRST_KEY).value(classic));
        }
    }

    @Nested
    @DisplayName("parameters")
    class Parameters {

        @Test
        void rejectsAnUnknownListWith400() throws Exception {
            mockMvc.perform(get(LIST_PATH).param(LIST_PARAM, "BESTSELLERS"))
                .andExpect(status().isBadRequest());
        }

        @Test
        void rejectsALimitOverTheCapWith400() throws Exception {
            mockMvc.perform(get(LIST_PATH).param(LIST_PARAM, TOP_RATED).param(LIMIT_PARAM, "9999"))
                .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReturn400WhenLimitIsZero() throws Exception {
            mockMvc.perform(get(LIST_PATH).param(LIST_PARAM, TOP_RATED).param(LIMIT_PARAM, "0"))
                .andExpect(status().isBadRequest());
        }

        @Test
        void honorsTheLimit() throws Exception {
            saveBook("one", "One", new BigDecimal("4.50"), WELL_RATED_COUNT);
            saveBook("two", "Two", new BigDecimal("4.40"), WELL_RATED_COUNT);
            saveBook("three", "Three", FOUR_POINT_THREE, WELL_RATED_COUNT);

            mockMvc.perform(get(LIST_PATH).param(LIST_PARAM, TOP_RATED).param(LIMIT_PARAM, "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(DATA_LENGTH).value(2));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/books/count")
    class Count {

        @Test
        void shouldReturnNumberOfBooksInCatalog() throws Exception {
            saveBook("red-rising", "Red Rising", FOUR_POINT_ZERO, WELL_RATED_COUNT);
            saveBook("golden-son", "Golden Son", null, 0);

            mockMvc.perform(get(LIST_PATH + "/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));
        }
    }

    private void saveBook(
        final String key, final String title, final BigDecimal average, final int ratingCount) {
        final Author author = authors.save(Books.author("Author of " + key));
        final Book book = Books.promoted(key, title, author);
        book.setAverageRating(average);
        book.setRatingCount(ratingCount);
        books.save(book);
    }

    private void setCreatedAt(final String key, final String instant) {
        jdbcTemplate.update(
            "UPDATE book SET created_at = ?::timestamptz WHERE dedup_key = ?", instant, key);
    }
}
