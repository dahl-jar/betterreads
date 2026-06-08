package com.betterreads.collections;

import com.betterreads.auth.ratelimit.RateLimitFilter;
import com.betterreads.catalog.entity.Book;
import com.betterreads.catalog.repository.BookRepository;
import com.betterreads.support.ContainerizedTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;

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
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the reading-shelf flow end-to-end: set a status, change it, toggle the favorite flag,
 * patch dates and notes, remove an entry, and list the shelf filtered by status.
 *
 * <p>Every shelf field the frontend reads (status, favorite, started and finished dates, notes) is
 * carried on the response, so each test asserts the JSON the endpoint returns rather than reading
 * the row back with SQL. Books are seeded into {@code book} with a known dedup key, the same public
 * key search and detail use; users register and log in for a real access JWT.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "jwt.secret=integration-test-secret-must-be-at-least-256-bits-long-padding-padding",
    "jwt.issuer=betterreads-it",
    "jwt.expiration-minutes=120",
    "jwt.refresh-expiration-days=30",
    "auth.refresh-cookie.secure=true",
    "auth.rate-limit.register-capacity=1000",
    "auth.rate-limit.register-refill-tokens=1000",
    "auth.rate-limit.register-refill-seconds=1",
    "auth.rate-limit.login-capacity=1000",
    "auth.rate-limit.login-refill-tokens=1000",
    "auth.rate-limit.login-refill-seconds=1",
    "mail.app-base-url=https://test.example.com",
    "mail.outbox.worker-enabled=false"
})
@SuppressWarnings("PMD.TooManyMethods")
class ShelvesIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String REGISTER_URL = "/api/v1/auth/register";

    private static final String LOGIN_URL = "/api/v1/auth/login";

    private static final String SHELF_URL = "/api/v1/me/books";

    private static final String AUTH_HEADER = "Authorization";

    private static final String BEARER_PREFIX = "Bearer ";

    private static final String DUNE_KEY = "OL893415W";

    private static final String DUNE_TITLE = "Dune";

    private static final String HOBBIT_KEY = "OL262758W";

    private static final String HOBBIT_TITLE = "The Hobbit";

    private static final String PASSWORD = "Sup3rSecret!";

    private static final String PASSWORD_FIELD = "password";

    private static final String DARROW = "darrow";

    private static final String DARROW_EMAIL = "darrow@example.com";

    private static final String GOBLIN = "goblin";

    private static final String GOBLIN_EMAIL = "goblin@example.com";

    private static final String WANT_TO_READ = "WANT_TO_READ";

    private static final String CURRENTLY_READING = "CURRENTLY_READING";

    private static final String FINISHED = "FINISHED";

    private static final String STATUS_FIELD = "status";

    private static final String JSON_STATUS = "$.status";

    private static final String JSON_FAVORITE = "$.favorite";

    private static final String JSON_STARTED = "$.startedAt";

    private static final String JSON_FINISHED = "$.finishedAt";

    private static final String JSON_LENGTH = "$.length()";

    private static final String STATUS_SUFFIX = "/status";

    private static final String START_DATE = "2026-01-02";

    private static final String FINISH_DATE = "2026-02-14";

    private static final String NOTE = "reread before the film";

    private static final String JSON_NOTES = "$.notes";

    private static final String BAD_STATUS = "NOT_A_STATUS";

    private static final String READING_NOTE = "still going";

    private static final String RATED_KEY = "OL27448W";

    private static final String RATED_TITLE = "The Way of Kings";

    private static final BigDecimal RATED_AVERAGE = new BigDecimal("4.50");

    private static final String JSON_ADDED_AT = "$.addedAt";

    private static final String JSON_AVERAGE_RATING = "$.averageRating";

    private static final String JSON_MY_RATING = "$.myRating";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .addFilter(springSecurityFilterChain)
            .build();
        jdbcTemplate.update("DELETE FROM user_book_collection");
        jdbcTemplate.update("DELETE FROM book_author");
        jdbcTemplate.update("DELETE FROM book");
        jdbcTemplate.update("DELETE FROM app_user");
        rateLimitFilter.reset();
        seedBook(DUNE_KEY, DUNE_TITLE);
        seedBook(HOBBIT_KEY, HOBBIT_TITLE);
        seedRatedBook(RATED_KEY, RATED_TITLE, RATED_AVERAGE);
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
                .andExpect(jsonPath("$.key").value(DUNE_KEY))
                .andExpect(jsonPath("$.title").value(DUNE_TITLE))
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
                .andExpect(jsonPath("$[0].status").value(CURRENTLY_READING));
        }

        @Test
        void enteringReadingStampsTheStartedDate() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);

            final ResultActions response = putStatus(token, DUNE_KEY, CURRENTLY_READING);

            response
                .andExpect(jsonPath(JSON_STARTED).isNotEmpty())
                .andExpect(jsonPath(JSON_FINISHED).doesNotExist());
        }

        @Test
        void markingFinishedStampsTheFinishedDate() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);

            final ResultActions response = putStatus(token, DUNE_KEY, FINISHED);

            response.andExpect(jsonPath(JSON_FINISHED).isNotEmpty());
        }

        @Test
        void rereadingKeepsTheOriginalStartDate() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);
            final String firstStarted = readJson(putStatus(token, DUNE_KEY, CURRENTLY_READING), JSON_STARTED);
            putStatus(token, DUNE_KEY, FINISHED);

            final ResultActions reread = putStatus(token, DUNE_KEY, CURRENTLY_READING);

            reread.andExpect(jsonPath(JSON_STARTED).value(firstStarted));
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
        void patchSucceedsAfterFinishedThenReadingTransition() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);
            putStatus(token, DUNE_KEY, FINISHED);
            putStatus(token, DUNE_KEY, CURRENTLY_READING);

            final ResultActions response = patchEntry(token, DUNE_KEY, null, null, READING_NOTE);

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_NOTES).value(READING_NOTE));
        }

        @Test
        void unknownStatusValueIsRejected() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);

            final ResultActions response = putStatus(token, DUNE_KEY, BAD_STATUS);

            response.andExpect(status().isBadRequest());
        }

        @Test
        void unknownStatusFilterReturns400() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);

            final ResultActions response = getShelf(token, BAD_STATUS);

            response.andExpect(status().isBadRequest());
        }

        @Test
        void shelvingUnknownBookKeyReturns404() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);

            final ResultActions response = putStatus(token, "OL000000W", CURRENTLY_READING);

            response.andExpect(status().isNotFound());
        }

        @Test
        void unauthenticatedSetStatusReturns401() throws Exception {
            final ResultActions response = mockMvc.perform(put(SHELF_URL + "/" + DUNE_KEY + STATUS_SUFFIX)
                .contentType(MediaType.APPLICATION_JSON)
                .content(statusPayload(CURRENTLY_READING)));

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

            mockMvc.perform(delete(SHELF_URL + "/" + DUNE_KEY).header(AUTH_HEADER, BEARER_PREFIX + token))
                .andExpect(status().isNoContent());

            final ResultActions shelf = getShelf(token, null);
            shelf.andExpect(jsonPath(JSON_LENGTH).value(0));
        }

        @Test
        void deletingAnUnshelvedBookIsANoOp() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);

            final ResultActions response = mockMvc.perform(
                delete(SHELF_URL + "/" + DUNE_KEY).header(AUTH_HEADER, BEARER_PREFIX + token));

            response.andExpect(status().isNoContent());
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
                .andExpect(jsonPath("$[0].key").value(DUNE_KEY));
        }

        @Test
        void withoutAFilterReturnsEveryShelvedBook() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);
            putStatus(token, DUNE_KEY, CURRENTLY_READING);
            putStatus(token, HOBBIT_KEY, FINISHED);

            final ResultActions response = getShelf(token, null);

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_LENGTH).value(2));
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
        void averageRatingCarriesTheBooksCommunityRating() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);

            final ResultActions response = putStatus(token, RATED_KEY, WANT_TO_READ);

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_AVERAGE_RATING).value(RATED_AVERAGE.doubleValue()));
        }

        @Test
        void averageRatingIsAbsentWhenTheBookHasNoCommunityRating() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);

            final ResultActions response = putStatus(token, DUNE_KEY, WANT_TO_READ);

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_AVERAGE_RATING).doesNotExist());
        }

        @Test
        void myRatingIsAbsentUntilTheReaderRatesTheBook() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);

            final ResultActions response = putStatus(token, RATED_KEY, WANT_TO_READ);

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_MY_RATING).doesNotExist());
        }
    }

    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private ResultActions putStatus(final String token, final String key, final String value)
        throws Exception {
        return mockMvc.perform(put(SHELF_URL + "/" + key + STATUS_SUFFIX)
            .header(AUTH_HEADER, BEARER_PREFIX + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(statusPayload(value)));
    }

    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private ResultActions putFavorite(final String token, final String key, final boolean value)
        throws Exception {
        return mockMvc.perform(put(SHELF_URL + "/" + key + "/favorite")
            .header(AUTH_HEADER, BEARER_PREFIX + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(favoritePayload(value)));
    }

    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private ResultActions patchEntry(final String token, final String key,
        final @Nullable String startedAt, final @Nullable String finishedAt,
        final @Nullable String notes) throws Exception {
        return mockMvc.perform(patch(SHELF_URL + "/" + key)
            .header(AUTH_HEADER, BEARER_PREFIX + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(patchPayload(startedAt, finishedAt, notes)));
    }

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

    private void seedBook(final String key, final String title) {
        seedRatedBook(key, title, null);
    }

    private void seedRatedBook(final String key, final String title,
        final @Nullable BigDecimal averageRating) {
        final Book book = new Book();
        book.setTitle(title);
        book.setDedupKey(key);
        book.setAverageRating(averageRating);
        bookRepository.save(book);
    }

    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private String registerAndLogin(final String username, final String email) throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerPayload(username, email)))
            .andExpect(status().isCreated());
        final MvcResult login = mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPayload(username)))
            .andExpect(status().isOk())
            .andReturn();
        final String body = login.getResponse().getContentAsString();
        return objectMapper.readTree(body).at("/accessToken").asString();
    }

    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private String readJson(final ResultActions actions, final String pointer) throws Exception {
        final String jsonPointer = pointer.replace("$.", "/");
        final String body = actions.andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at(jsonPointer).asString();
    }

    private String registerPayload(final String username, final String email) {
        final ObjectNode node = objectMapper.createObjectNode();
        node.put("username", username);
        node.put("email", email);
        node.put(PASSWORD_FIELD, PASSWORD);
        return objectMapper.writeValueAsString(node);
    }

    private String loginPayload(final String identifier) {
        final ObjectNode node = objectMapper.createObjectNode();
        node.put("identifier", identifier);
        node.put(PASSWORD_FIELD, PASSWORD);
        return objectMapper.writeValueAsString(node);
    }

    private String statusPayload(final String value) {
        final ObjectNode node = objectMapper.createObjectNode();
        node.put(STATUS_FIELD, value);
        return objectMapper.writeValueAsString(node);
    }

    private String favoritePayload(final boolean value) {
        final ObjectNode node = objectMapper.createObjectNode();
        node.put("favorite", value);
        return objectMapper.writeValueAsString(node);
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
