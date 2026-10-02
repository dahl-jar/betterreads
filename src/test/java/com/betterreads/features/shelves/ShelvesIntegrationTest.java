package com.betterreads.features.shelves;

import com.betterreads.book.Book;
import com.betterreads.book.BookRepository;
import com.betterreads.testsupport.Books;
import com.betterreads.testsupport.RegisteredUserTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.betterreads.testsupport.Books.DUNE_KEY;
import static com.betterreads.testsupport.Books.DUNE_TITLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
class ShelvesIntegrationTest extends RegisteredUserTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String SHELF_URL = "/api/v1/me/books";

    private static final String AUTH_HEADER = "Authorization";

    private static final String BEARER_PREFIX = "Bearer ";

    private static final String HOBBIT_KEY = "OL262758W";

    private static final String HOBBIT_TITLE = "The Hobbit";

    private static final String DARROW = "darrow";

    private static final String DARROW_EMAIL = "darrow@example.com";

    private static final String GOBLIN = "goblin";

    private static final String GOBLIN_EMAIL = "goblin@example.com";

    private static final String WANT_TO_READ = "WANT_TO_READ";

    private static final String CURRENTLY_READING = "CURRENTLY_READING";

    private static final String FINISHED = "FINISHED";

    private static final String STATUS_FIELD = "status";

    private static final String JSON_STATUS = "$.data.status";

    private static final String JSON_FAVORITE = "$.data.favorite";

    private static final String JSON_STARTED = "$.data.startedAt";

    private static final String JSON_FINISHED = "$.data.finishedAt";

    private static final String JSON_LENGTH = "$.data.length()";

    private static final String STATUS_SUFFIX = "/status";

    private static final String START_DATE = "2026-01-02";

    private static final String FINISH_DATE = "2026-02-14";

    private static final String NOTE = "reread before the film";

    private static final String JSON_NOTES = "$.data.notes";

    private static final String BAD_STATUS = "NOT_A_STATUS";

    private static final String READING_NOTE = "still going";

    private static final String RATED_KEY = "OL27448W";

    private static final String RATED_TITLE = "The Way of Kings";

    private static final BigDecimal RATED_AVERAGE = new BigDecimal("4.50");

    private static final int GIVEN_RATING = 4;

    private static final String JSON_ADDED_AT = "$.data.addedAt";

    private static final String JSON_AVERAGE_RATING = "$.data.averageRating";

    private static final String JSON_MY_RATING = "$.data.myRating";

    private static final String JSON_FIRST_MY_RATING = "$.data[0].myRating";

    private static final String JSON_FIRST_KEY = "$.data[0].key";

    private static final String UNKNOWN_KEY = "OL000000W";

    private static final String OVERSIZED_NOTE = "x".repeat(2001);

    private static final String DROPPED = "DROPPED";

    private static final String BOOKS_PATH = "/api/v1/books/";

    private static final int THREE_READERS = 3;

    private static final int FOUR_READERS = 4;

    private static final String JSON_WANT_TO_READ_COUNT = "$.data.wantToRead";

    private static final String JSON_CURRENTLY_READING_COUNT = "$.data.currentlyReading";

    private static final String JSON_FINISHED_COUNT = "$.data.finished";

    private static final String JSON_DROPPED_COUNT = "$.data.dropped";

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = securedMockMvc();
        jdbcTemplate.update("DELETE FROM user_book_collection");
        resetToDune();
        Books.seedBook(bookRepository, HOBBIT_KEY, HOBBIT_TITLE);
        final Book rated = Books.book(RATED_KEY, RATED_TITLE);
        rated.setAverageRating(RATED_AVERAGE);
        bookRepository.save(rated);
    }

    @Nested
    @DisplayName("PUT /me/books/{key}/status")
    class SetStatus {

        @Test
        void firstStatusInsertsTheShelfRowAtThatStatus() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);

            final ResultActions response = putStatus(token, DUNE_KEY, WANT_TO_READ);

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.key").value(DUNE_KEY))
                .andExpect(jsonPath("$.data.title").value(DUNE_TITLE))
                .andExpect(jsonPath(JSON_STATUS).value(WANT_TO_READ))
                .andExpect(jsonPath(JSON_FAVORITE).value(false));
        }

        @Test
        void changingStatusUpdatesTheSameRow() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);
            putStatus(token, DUNE_KEY, WANT_TO_READ);

            putStatus(token, DUNE_KEY, CURRENTLY_READING);

            final ResultActions shelf = getShelf(token, null);
            shelf
                .andExpect(jsonPath(JSON_LENGTH).value(1))
                .andExpect(jsonPath("$.data[0].status").value(CURRENTLY_READING));
        }

        @Test
        void enteringReadingStampsTheStartedDate() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);
            final LocalDate today = LocalDate.now(ZoneOffset.UTC);

            final ResultActions response = putStatus(token, DUNE_KEY, CURRENTLY_READING);

            response
                .andExpect(jsonPath(JSON_STARTED).value(today.toString()))
                .andExpect(jsonPath(JSON_FINISHED).doesNotExist());
        }

        @Test
        void markingFinishedStampsTheFinishedDate() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);
            final LocalDate today = LocalDate.now(ZoneOffset.UTC);

            final ResultActions response = putStatus(token, DUNE_KEY, FINISHED);

            response.andExpect(jsonPath(JSON_FINISHED).value(today.toString()));
        }

        @Test
        void rereadingKeepsTheOriginalStartDate() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);
            putStatus(token, DUNE_KEY, CURRENTLY_READING);
            patchEntry(token, DUNE_KEY, START_DATE, null, null);
            putStatus(token, DUNE_KEY, FINISHED);

            final ResultActions reread = putStatus(token, DUNE_KEY, CURRENTLY_READING);

            reread.andExpect(jsonPath(JSON_STARTED).value(START_DATE));
        }

        @Test
        void shouldKeepTheFinishDateWhenFinishedAgain() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);
            putStatus(token, DUNE_KEY, FINISHED);
            patchEntry(token, DUNE_KEY, START_DATE, FINISH_DATE, null);

            final ResultActions response = putStatus(token, DUNE_KEY, FINISHED);

            response.andExpect(jsonPath(JSON_FINISHED).value(FINISH_DATE));
        }

        @Test
        void movingFromFinishedBackToReadingClearsTheFinishedDate() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);
            putStatus(token, DUNE_KEY, FINISHED);

            final ResultActions response = putStatus(token, DUNE_KEY, CURRENTLY_READING);

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_STARTED).isNotEmpty())
                .andExpect(jsonPath(JSON_FINISHED).doesNotExist());
        }

        @Test
        void unknownStatusValueIsRejected() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);

            final ResultActions response = putStatus(token, DUNE_KEY, BAD_STATUS);

            response.andExpect(status().isBadRequest());
        }

        @Test
        void shelvingUnknownBookKeyReturns404() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);

            final ResultActions response = putStatus(token, UNKNOWN_KEY, CURRENTLY_READING);

            response.andExpect(status().isNotFound());
        }

        @Test
        void unauthenticatedSetStatusReturns401() throws Exception {
            final ResultActions response = mockMvc.perform(put(SHELF_URL + "/" + DUNE_KEY + STATUS_SUFFIX)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody(STATUS_FIELD, CURRENTLY_READING)));

            response.andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("PUT /me/books/{key}/favorite")
    class Favorite {

        @Test
        void favoriteIsSeparateFromStatus() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);
            putStatus(token, DUNE_KEY, FINISHED);

            final ResultActions response = putFavorite(token, DUNE_KEY, true);

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_STATUS).value(FINISHED))
                .andExpect(jsonPath(JSON_FAVORITE).value(true));
        }

        @Test
        void favoritingAnUnshelvedBookOpensTheRowAtWantToRead() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);

            final ResultActions response = putFavorite(token, DUNE_KEY, true);

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_FAVORITE).value(true))
                .andExpect(jsonPath(JSON_STATUS).value(WANT_TO_READ));
        }

        @Test
        void shouldClearTheFavoriteWhenSetToFalse() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);
            putFavorite(token, DUNE_KEY, true);

            final ResultActions response = putFavorite(token, DUNE_KEY, false);

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_FAVORITE).value(false));
        }
    }

    @Nested
    @DisplayName("PATCH /me/books/{key}")
    class PatchEntry {

        @Test
        void datesAndNotesArePersisted() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);
            putStatus(token, DUNE_KEY, CURRENTLY_READING);

            final ResultActions response = patchEntry(token, DUNE_KEY, START_DATE, FINISH_DATE, NOTE);

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_STARTED).value(START_DATE))
                .andExpect(jsonPath(JSON_FINISHED).value(FINISH_DATE))
                .andExpect(jsonPath(JSON_NOTES).value(NOTE));
        }

        @Test
        void finishedBeforeStartedIsRejected() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);
            putStatus(token, DUNE_KEY, CURRENTLY_READING);

            final ResultActions response = patchEntry(token, DUNE_KEY, FINISH_DATE, START_DATE, null);

            response.andExpect(status().isBadRequest());
        }

        @Test
        void shouldRejectAFinishDateBeforeTheStoredStartDate() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);
            putStatus(token, DUNE_KEY, CURRENTLY_READING);
            patchEntry(token, DUNE_KEY, FINISH_DATE, null, null);

            final ResultActions response = patchEntry(token, DUNE_KEY, null, START_DATE, null);

            response.andExpect(status().isBadRequest());
            final ResultActions shelf = getShelf(token, null);
            shelf
                .andExpect(jsonPath("$.data[0].startedAt").value(FINISH_DATE))
                .andExpect(jsonPath("$.data[0].finishedAt").doesNotExist());
        }

        @Test
        void shouldRejectANoteOver2000Characters() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);
            putStatus(token, DUNE_KEY, CURRENTLY_READING);

            final ResultActions response = patchEntry(token, DUNE_KEY, null, null, OVERSIZED_NOTE);

            response.andExpect(status().isBadRequest());
        }

        @Test
        void shouldKeepTheStoredNoteWhenOnlyDatesArePatched() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);
            putStatus(token, DUNE_KEY, CURRENTLY_READING);
            patchEntry(token, DUNE_KEY, null, null, NOTE);

            final ResultActions response = patchEntry(token, DUNE_KEY, START_DATE, null, null);

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_NOTES).value(NOTE));
        }

        @Test
        void patchingOnlyTheNoteKeepsTheStoredDates() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);
            putStatus(token, DUNE_KEY, CURRENTLY_READING);
            patchEntry(token, DUNE_KEY, START_DATE, FINISH_DATE, NOTE);

            final ResultActions response = patchEntry(token, DUNE_KEY, null, null, READING_NOTE);

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_NOTES).value(READING_NOTE))
                .andExpect(jsonPath(JSON_STARTED).value(START_DATE))
                .andExpect(jsonPath(JSON_FINISHED).value(FINISH_DATE));
        }

        @Test
        void patchingAnUnshelvedBookReturns404() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);

            final ResultActions response = patchEntry(token, DUNE_KEY, START_DATE, null, null);

            response.andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("DELETE /me/books/{key}")
    class RemoveEntry {

        @Test
        void deleteRemovesTheBookFromTheShelf() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);
            putStatus(token, DUNE_KEY, CURRENTLY_READING);

            final ResultActions removed = deleteEntry(token, DUNE_KEY);
            removed.andExpect(status().isNoContent());

            final ResultActions shelf = getShelf(token, null);
            shelf.andExpect(jsonPath(JSON_LENGTH).value(0));
        }

        @Test
        void deletingAnUnshelvedBookIsANoOp() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);

            final ResultActions response = deleteEntry(token, DUNE_KEY);

            response.andExpect(status().isNoContent());
        }

        @Test
        void shouldReturn404WhenDeletingAnUnknownBook() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);

            final ResultActions response = deleteEntry(token, UNKNOWN_KEY);

            response.andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /me/books")
    class ListShelf {

        @Test
        void filtersByStatus() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);
            putStatus(token, DUNE_KEY, CURRENTLY_READING);
            putStatus(token, HOBBIT_KEY, FINISHED);

            final ResultActions response = getShelf(token, CURRENTLY_READING);

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_LENGTH).value(1))
                .andExpect(jsonPath(JSON_FIRST_KEY).value(DUNE_KEY));
        }

        @Test
        void withoutAFilterReturnsEveryShelvedBook() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);
            putStatus(token, DUNE_KEY, CURRENTLY_READING);
            putStatus(token, HOBBIT_KEY, FINISHED);

            final ResultActions response = getShelf(token, null);

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_LENGTH).value(2))
                .andExpect(jsonPath(JSON_FIRST_KEY).value(HOBBIT_KEY))
                .andExpect(jsonPath("$.data[1].key").value(DUNE_KEY));
        }

        @Test
        void unknownStatusFilterReturns400() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);

            final ResultActions response = getShelf(token, BAD_STATUS);

            response.andExpect(status().isBadRequest());
        }

        @Test
        void oneUsersShelfIsInvisibleToAnother() throws Exception {
            final String darrowToken = registerAndLogin(DARROW, DARROW_EMAIL);
            final String goblinToken = registerAndLogin(GOBLIN, GOBLIN_EMAIL);
            putStatus(darrowToken, DUNE_KEY, CURRENTLY_READING);

            final ResultActions goblinShelf = getShelf(goblinToken, null);

            goblinShelf
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_LENGTH).value(0));
        }
    }

    @Nested
    @DisplayName("Shelf response fields")
    class ShelfResponseFields {

        @Test
        void shelvingABookRecordsTheDateItWasAdded() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);
            final LocalDate today = LocalDate.now(ZoneOffset.UTC);

            final ResultActions response = putStatus(token, DUNE_KEY, WANT_TO_READ);

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_ADDED_AT).value(today.toString()));
        }

        @Test
        void averageRatingCarriesTheBooksSourceRating() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);

            final ResultActions response = putStatus(token, RATED_KEY, WANT_TO_READ);

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_AVERAGE_RATING).value(RATED_AVERAGE.doubleValue()));
        }

        @Test
        void shouldShowMyRatingWhenShelvingARatedBook() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);
            rateBook(token, RATED_KEY, GIVEN_RATING);

            final ResultActions response = putStatus(token, RATED_KEY, WANT_TO_READ);

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_MY_RATING).value(GIVEN_RATING));
        }

        @Test
        void ratedBookShowsTheRatingOnTheShelf() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);
            putStatus(token, RATED_KEY, WANT_TO_READ);
            rateBook(token, RATED_KEY, GIVEN_RATING);

            final ResultActions shelf = getShelf(token, null);

            shelf
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_FIRST_MY_RATING).value(GIVEN_RATING));
        }

        @Test
        void oneUsersRatingIsInvisibleToAnother() throws Exception {
            final String darrowToken = registerAndLogin(DARROW, DARROW_EMAIL);
            final String goblinToken = registerAndLogin(GOBLIN, GOBLIN_EMAIL);
            rateBook(darrowToken, RATED_KEY, GIVEN_RATING);
            putStatus(goblinToken, RATED_KEY, WANT_TO_READ);

            final ResultActions goblinShelf = getShelf(goblinToken, null);

            goblinShelf
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_FIRST_KEY).value(RATED_KEY))
                .andExpect(jsonPath(JSON_FIRST_MY_RATING).doesNotExist());
        }
    }

    @Nested
    @DisplayName("GET /books/{key}/shelf-counts")
    class ShelfCounts {

        @Test
        void shouldCountReadersPerStatus() throws Exception {
            shelveForEach(DUNE_KEY, WANT_TO_READ, DARROW);
            shelveForEach(DUNE_KEY, CURRENTLY_READING, GOBLIN, "mustang");
            shelveForEach(DUNE_KEY, FINISHED, "sevro", "cassius", "victra");
            shelveForEach(DUNE_KEY, DROPPED, "ragnar", "roque", "dancer", "holiday");

            final ResultActions response = getShelfCounts(DUNE_KEY);

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_WANT_TO_READ_COUNT).value(1))
                .andExpect(jsonPath(JSON_CURRENTLY_READING_COUNT).value(2))
                .andExpect(jsonPath(JSON_FINISHED_COUNT).value(THREE_READERS))
                .andExpect(jsonPath(JSON_DROPPED_COUNT).value(FOUR_READERS));
        }

        @Test
        void shouldReturnZeroForEveryStatusWhenNobodyShelvedTheBook() throws Exception {
            final ResultActions response = getShelfCounts(DUNE_KEY);

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_WANT_TO_READ_COUNT).value(0))
                .andExpect(jsonPath(JSON_CURRENTLY_READING_COUNT).value(0))
                .andExpect(jsonPath(JSON_FINISHED_COUNT).value(0))
                .andExpect(jsonPath(JSON_DROPPED_COUNT).value(0));
        }

        @Test
        void shouldNotCountReadersOfAnotherBook() throws Exception {
            shelveForEach(HOBBIT_KEY, FINISHED, DARROW);
            shelveForEach(DUNE_KEY, FINISHED, GOBLIN);

            final ResultActions response = getShelfCounts(DUNE_KEY);

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_FINISHED_COUNT).value(1));
        }

        @Test
        void shouldNotCountReadersWithDeletedAccounts() throws Exception {
            shelveForEach(DUNE_KEY, FINISHED, DARROW, GOBLIN);
            final int deleted = jdbcTemplate.update(
                "UPDATE app_user SET deleted_at = now() WHERE username = ?", GOBLIN);
            assertThat(deleted).isOne();

            final ResultActions response = getShelfCounts(DUNE_KEY);

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_FINISHED_COUNT).value(1));
        }

        @Test
        void shouldReturn404ForShelfCountsOfAnUnknownBook() throws Exception {
            final ResultActions response = getShelfCounts(UNKNOWN_KEY);

            response
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(containsString(UNKNOWN_KEY)));
        }
    }

    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private void shelveForEach(final String key, final String readingStatus, final String... readers)
        throws Exception {
        for (final String reader : readers) {
            final String token = registerAndLogin(reader, reader + "@example.com");
            final ResultActions shelved = putStatus(token, key, readingStatus);
            shelved.andExpect(status().isOk());
        }
    }

    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private ResultActions getShelfCounts(final String key) throws Exception {
        return mockMvc.perform(get(BOOKS_PATH + key + "/shelf-counts"));
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private ResultActions putStatus(final String token, final String key, final String value)
        throws Exception {
        return mockMvc.perform(put(SHELF_URL + "/" + key + STATUS_SUFFIX)
            .header(AUTH_HEADER, BEARER_PREFIX + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(jsonBody(STATUS_FIELD, value)));
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private ResultActions putFavorite(final String token, final String key, final boolean value)
        throws Exception {
        return mockMvc.perform(put(SHELF_URL + "/" + key + "/favorite")
            .header(AUTH_HEADER, BEARER_PREFIX + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(jsonBody("favorite", value)));
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private ResultActions patchEntry(final String token, final String key,
        final @Nullable String startedAt, final @Nullable String finishedAt,
        final @Nullable String notes) throws Exception {
        return mockMvc.perform(patch(SHELF_URL + "/" + key)
            .header(AUTH_HEADER, BEARER_PREFIX + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(patchPayload(startedAt, finishedAt, notes)));
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private ResultActions deleteEntry(final String token, final String key) throws Exception {
        return mockMvc.perform(delete(SHELF_URL + "/" + key).header(AUTH_HEADER, BEARER_PREFIX + token));
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private ResultActions getShelf(final String token, final @Nullable String statusFilter)
        throws Exception {
        final MockHttpServletRequestBuilder request =
            get(SHELF_URL).header(AUTH_HEADER, BEARER_PREFIX + token);
        if (statusFilter != null) {
            request.param(STATUS_FIELD, statusFilter);
        }
        return mockMvc.perform(request);
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private void rateBook(final String token, final String key, final int rating) throws Exception {
        mockMvc.perform(put(BOOKS_PATH + key + "/reviews/me")
                .header(AUTH_HEADER, BEARER_PREFIX + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody("rating", rating)))
            .andExpect(status().isOk());
    }

    private String jsonBody(final String field, final Object value) {
        return objectMapper.writeValueAsString(Map.of(field, value));
    }

    private String patchPayload(final @Nullable String startedAt, final @Nullable String finishedAt,
        final @Nullable String notes) {
        final ObjectNode node = objectMapper.createObjectNode();
        node.put("startedAt", startedAt);
        node.put("finishedAt", finishedAt);
        node.put("notes", notes);
        return objectMapper.writeValueAsString(node);
    }
}
