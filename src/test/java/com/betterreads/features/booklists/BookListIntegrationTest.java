package com.betterreads.features.booklists;

import static com.betterreads.testsupport.Books.DUNE_TITLE;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.betterreads.book.Author;
import com.betterreads.book.AuthorRepository;
import com.betterreads.book.Book;
import com.betterreads.book.BookRepository;
import com.betterreads.booksource.SeriesEntry;
import com.betterreads.ratelimit.RateLimitFilter;
import com.betterreads.testsupport.Books;
import com.betterreads.testsupport.ContainerizedTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;
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
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Book lists, the catalog count, and a book's series against a real Postgres. */
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

    private static final String SERIES_PATH = "/api/v1/books/{key}/series";

    private static final String RED_RISING = "red-rising";

    private static final String GOLDEN_SON = "golden-son";

    private static final String MORNING_STAR = "morning-star";

    private static final String EMPIRE_OF_SILENCE = "empire-of-silence";

    private static final String SAGA = "Red Rising Saga";

    private static final String SUN_EATER = "The Sun Eater";

    private static final String AUTHOR_PREFIX = "Author of ";

    private static final String TITLE_PREFIX = "Title of ";

    private static final String FIRST_SERIES_BOOK_KEYS = "$.data[0].books[*].key";

    private static final String FIRST_SERIES_FIRST_BOOK = "$.data[0].books[0]";

    private static final String COVER_PATH = "/api/v1/images/covers/";

    private static final int SERIES_BOOK_CAP = 50;

    private static final int THIRD_VOLUME = 3;

    private static final int FOURTH_VOLUME = 4;

    @Autowired
    private BookRepository books;

    @Autowired
    private AuthorRepository authors;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = securedMockMvc();
        rateLimitFilter.reset();
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
                .andExpect(jsonPath("$.data[0].coverUrl").value(containsString(COVER_PATH + dune)))
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
            saveBook(RED_RISING, "Red Rising", FOUR_POINT_ZERO, WELL_RATED_COUNT);
            saveBook(GOLDEN_SON, "Golden Son", null, 0);

            mockMvc.perform(get(LIST_PATH + "/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/books/{key}/series")
    class Series {

        @Test
        void shouldListTheOtherBooksOfASeriesByPosition() throws Exception {
            saveInSeries(GOLDEN_SON, new SeriesEntry(SAGA, 2));
            saveInSeries(RED_RISING, new SeriesEntry(SAGA, 1));
            saveInSeries(MORNING_STAR, new SeriesEntry(SAGA, THIRD_VOLUME));

            final ResultActions response = mockMvc.perform(get(SERIES_PATH, MORNING_STAR));

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(FIRST_SERIES_FIRST_BOOK + ".key").value(RED_RISING))
                .andExpect(jsonPath("$.data[0].books[1].key").value(GOLDEN_SON));
        }

        @Test
        void shouldListBooksAtTheSamePositionInTheOrderTheyWereAdded() throws Exception {
            saveInSeries(RED_RISING, new SeriesEntry(SAGA, 1));
            saveInSeries(GOLDEN_SON, new SeriesEntry(SAGA, 2));
            saveInSeries(MORNING_STAR, new SeriesEntry(SAGA, 2));

            final ResultActions response = mockMvc.perform(get(SERIES_PATH, RED_RISING));

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(FIRST_SERIES_BOOK_KEYS, contains(GOLDEN_SON, MORNING_STAR)));
        }

        @Test
        void shouldLeaveTheRequestedBookOutOfItsSeries() throws Exception {
            saveInSeries(RED_RISING, new SeriesEntry(SAGA, 1));
            saveInSeries(GOLDEN_SON, new SeriesEntry(SAGA, 2));

            final ResultActions response = mockMvc.perform(get(SERIES_PATH, RED_RISING));

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(FIRST_SERIES_BOOK_KEYS, contains(GOLDEN_SON)));
        }

        @Test
        void shouldLeaveOutBooksFromAnotherSeries() throws Exception {
            saveInSeries(RED_RISING, new SeriesEntry(SAGA, 1));
            saveInSeries(GOLDEN_SON, new SeriesEntry(SAGA, 2));
            saveInSeries(EMPIRE_OF_SILENCE, new SeriesEntry(SUN_EATER, 1));

            final ResultActions response = mockMvc.perform(get(SERIES_PATH, RED_RISING));

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(FIRST_SERIES_BOOK_KEYS, hasItem(GOLDEN_SON)))
                .andExpect(jsonPath(FIRST_SERIES_BOOK_KEYS, not(hasItem(EMPIRE_OF_SILENCE))));
        }

        @Test
        void shouldListEachSeriesOfABookInItsSavedOrder() throws Exception {
            saveInSeries(RED_RISING, new SeriesEntry(SUN_EATER, FOURTH_VOLUME), new SeriesEntry(SAGA, 1));

            final ResultActions response = mockMvc.perform(get(SERIES_PATH, RED_RISING));

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(DATA_LENGTH).value(2))
                .andExpect(jsonPath("$.data[0].name").value(SUN_EATER))
                .andExpect(jsonPath("$.data[0].position").value(FOURTH_VOLUME))
                .andExpect(jsonPath("$.data[1].name").value(SAGA))
                .andExpect(jsonPath("$.data[1].position").value(1));
        }

        @Test
        void shouldGiveEachBookItsPositionInThatSeries() throws Exception {
            saveInSeries(RED_RISING, new SeriesEntry(SAGA, 1), new SeriesEntry(SUN_EATER, THIRD_VOLUME));
            saveInSeries(GOLDEN_SON, new SeriesEntry(SUN_EATER, FOURTH_VOLUME), new SeriesEntry(SAGA, 2));

            final ResultActions response = mockMvc.perform(get(SERIES_PATH, RED_RISING));

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(FIRST_SERIES_FIRST_BOOK + ".position").value(2))
                .andExpect(jsonPath("$.data[1].books[0].position").value(FOURTH_VOLUME));
        }

        @Test
        void shouldReturnTheCardDetailsOfASeriesBook() throws Exception {
            saveInSeries(RED_RISING, new SeriesEntry(SAGA, 1));
            saveInSeries(GOLDEN_SON, new SeriesEntry(SAGA, 2));

            final ResultActions response = mockMvc.perform(get(SERIES_PATH, RED_RISING));

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(FIRST_SERIES_FIRST_BOOK + ".title").value(TITLE_PREFIX + GOLDEN_SON))
                .andExpect(jsonPath(FIRST_SERIES_FIRST_BOOK + ".authors", contains(AUTHOR_PREFIX + GOLDEN_SON)))
                .andExpect(jsonPath(FIRST_SERIES_FIRST_BOOK + ".coverUrl")
                    .value(containsString(COVER_PATH + GOLDEN_SON)));
        }

        @Test
        void shouldCapASeriesAtFiftyOtherBooks() throws Exception {
            final int lastPosition = SERIES_BOOK_CAP + 2;
            saveInSeries(RED_RISING, new SeriesEntry(SAGA, 1));
            IntStream.rangeClosed(2, lastPosition)
                .forEach(position -> saveInSeries("volume-" + position, new SeriesEntry(SAGA, position)));

            final ResultActions response = mockMvc.perform(get(SERIES_PATH, RED_RISING));

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].books.length()").value(SERIES_BOOK_CAP));
        }

        @Test
        void shouldReturnNoSeriesForAStandaloneBook() throws Exception {
            saveBook(RED_RISING, TITLE_PREFIX + RED_RISING, null, 0);

            final ResultActions response = mockMvc.perform(get(SERIES_PATH, RED_RISING));

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(DATA_LENGTH).value(0));
        }

        @Test
        void shouldReturn404ForSeriesOfAnUnknownBook() throws Exception {
            final String unknown = "no-such-book";

            final ResultActions response = mockMvc.perform(get(SERIES_PATH, unknown));

            response
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(containsString(unknown)));
        }
    }

    private void saveBook(
        final String key, final String title, final BigDecimal average, final int ratingCount) {
        final Author author = authors.save(Books.author(AUTHOR_PREFIX + key));
        final Book book = Books.promoted(key, title, author);
        book.setAverageRating(average);
        book.setRatingCount(ratingCount);
        books.save(book);
    }

    private void saveInSeries(final String key, final SeriesEntry... series) {
        final Author author = authors.save(Books.author(AUTHOR_PREFIX + key));
        final Book book = Books.promoted(key, TITLE_PREFIX + key, author);
        book.applySeries(List.of(series), true);
        books.save(book);
    }

    private void setCreatedAt(final String key, final String instant) {
        jdbcTemplate.update(
            "UPDATE book SET created_at = ?::timestamptz WHERE dedup_key = ?", instant, key);
    }
}
