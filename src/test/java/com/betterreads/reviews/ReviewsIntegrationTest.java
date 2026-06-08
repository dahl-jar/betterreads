package com.betterreads.reviews;

import com.betterreads.auth.ratelimit.RateLimitFilter;
import com.betterreads.catalog.entity.Book;
import com.betterreads.catalog.repository.BookRepository;
import com.betterreads.support.ContainerizedTest;

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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the review and rating flow end-to-end: post a review, edit it on a second submit, rate
 * without prose, list a book's reviews and the caller's own, and the community-rating recompute that
 * makes user ratings take over {@code book.average_rating} from the external seed.
 *
 * <p>Reviews are asserted through the JSON the endpoints return. Books are seeded into {@code book}
 * with a known dedup key; users register and log in for a real access JWT.
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
class ReviewsIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String REGISTER_URL = "/api/v1/auth/register";

    private static final String LOGIN_URL = "/api/v1/auth/login";

    private static final String AUTH_HEADER = "Authorization";

    private static final String BEARER_PREFIX = "Bearer ";

    private static final String DUNE_KEY = "OL893415W";

    private static final String DUNE_TITLE = "Dune";

    private static final String PASSWORD = "Sup3rSecret!";

    private static final String PASSWORD_FIELD = "password";

    private static final String DARROW = "darrow";

    private static final String DARROW_EMAIL = "darrow@example.com";

    private static final String GOBLIN = "goblin";

    private static final String GOBLIN_EMAIL = "goblin@example.com";

    private static final String REVIEW_TITLE = "A desert masterpiece";

    private static final String REVIEW_BODY = "The world-building carries the whole arc.";

    private static final int FIVE_STARS = 5;

    private static final int THREE_STARS = 3;

    private static final int RATING_BELOW_RANGE = 0;

    private static final int RATING_ABOVE_RANGE = 6;

    private static final int OVERLONG_BODY_LENGTH = 5001;

    private static final String EXTERNAL_RATING = "4.20";

    private static final int EXTERNAL_RATING_COUNT = 99;

    private static final String JSON_RATING = "$.data.rating";

    private static final String JSON_TITLE = "$.data.title";

    private static final String JSON_BODY = "$.data.body";

    private static final String JSON_LENGTH = "$.data.length()";

    private static final String JSON_FIRST_RATING = "$.data[0].rating";

    private static final String BOOKS_PATH = "/api/v1/books/";

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
        jdbcTemplate.update("DELETE FROM review");
        jdbcTemplate.update("DELETE FROM book_author");
        jdbcTemplate.update("DELETE FROM book");
        jdbcTemplate.update("DELETE FROM app_user");
        rateLimitFilter.reset();
        seedBook(DUNE_KEY, DUNE_TITLE);
    }

    @Nested
    @DisplayName("PUT /books/{key}/reviews (upsert)")
    class UpsertReview {

        @Test
        void postingAReviewReturnsItWithRatingTitleAndBody() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);

            final ResultActions response = putReview(token, DUNE_KEY, FIVE_STARS, REVIEW_TITLE, REVIEW_BODY);

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_RATING).value(FIVE_STARS))
                .andExpect(jsonPath(JSON_TITLE).value(REVIEW_TITLE))
                .andExpect(jsonPath(JSON_BODY).value(REVIEW_BODY));
        }

        @Test
        void ratingOnlyReviewHasNoProse() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);

            final ResultActions response = putReview(token, DUNE_KEY, FIVE_STARS, null, null);

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_RATING).value(FIVE_STARS))
                .andExpect(jsonPath(JSON_TITLE).doesNotExist())
                .andExpect(jsonPath(JSON_BODY).doesNotExist());
        }

        @Test
        void secondSubmitEditsTheSameReview() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);
            putReview(token, DUNE_KEY, FIVE_STARS, REVIEW_TITLE, REVIEW_BODY);

            putReview(token, DUNE_KEY, THREE_STARS, "Changed my mind", "Pacing drags in the middle.");

            final ResultActions list = getBookReviews(DUNE_KEY);
            list
                .andExpect(jsonPath(JSON_LENGTH).value(1))
                .andExpect(jsonPath(JSON_FIRST_RATING).value(THREE_STARS));
        }

        @Test
        void ratingBelowOneIsRejected() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);

            final ResultActions response = putReview(token, DUNE_KEY, RATING_BELOW_RANGE, null, null);

            response.andExpect(status().isBadRequest());
        }

        @Test
        void ratingAboveFiveIsRejected() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);

            final ResultActions response = putReview(token, DUNE_KEY, RATING_ABOVE_RANGE, null, null);

            response.andExpect(status().isBadRequest());
        }

        @Test
        void overlongBodyIsRejected() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);
            final String overlong = "x".repeat(OVERLONG_BODY_LENGTH);

            final ResultActions response = putReview(token, DUNE_KEY, FIVE_STARS, null, overlong);

            response.andExpect(status().isBadRequest());
        }

        @Test
        void reviewingAnUnknownBookReturns404() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);

            final ResultActions response = putReview(token, "OL000000W", FIVE_STARS, null, null);

            response.andExpect(status().isNotFound());
        }

        @Test
        void unauthenticatedReviewReturns401() throws Exception {
            final ResultActions response = mockMvc.perform(put(reviewUrl(DUNE_KEY))
                .contentType(MediaType.APPLICATION_JSON)
                .content(reviewPayload(FIVE_STARS, REVIEW_TITLE, REVIEW_BODY)));

            response.andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET review listings")
    class ListReviews {

        @Test
        void aBooksReviewsArePublic() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);
            putReview(token, DUNE_KEY, FIVE_STARS, REVIEW_TITLE, REVIEW_BODY);

            final ResultActions response = getBookReviews(DUNE_KEY);

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_LENGTH).value(1))
                .andExpect(jsonPath("$.data[0].title").value(REVIEW_TITLE));
        }

        @Test
        void myReviewsReturnsOnlyTheCallersReviews() throws Exception {
            final String darrowToken = registerAndLogin(DARROW, DARROW_EMAIL);
            final String goblinToken = registerAndLogin(GOBLIN, GOBLIN_EMAIL);
            putReview(darrowToken, DUNE_KEY, FIVE_STARS, REVIEW_TITLE, REVIEW_BODY);
            putReview(goblinToken, DUNE_KEY, THREE_STARS, null, null);

            final ResultActions response = mockMvc.perform(
                get("/api/v1/me/reviews").header(AUTH_HEADER, BEARER_PREFIX + goblinToken));

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_LENGTH).value(1))
                .andExpect(jsonPath(JSON_FIRST_RATING).value(THREE_STARS));
        }
    }

    @Nested
    @DisplayName("DELETE /books/{key}/reviews/me")
    class DeleteReview {

        @Test
        void aUserCanDeleteTheirOwnReview() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);
            putReview(token, DUNE_KEY, FIVE_STARS, REVIEW_TITLE, REVIEW_BODY);

            mockMvc.perform(delete(reviewUrl(DUNE_KEY)).header(AUTH_HEADER, BEARER_PREFIX + token))
                .andExpect(status().isNoContent());

            getBookReviews(DUNE_KEY).andExpect(jsonPath(JSON_LENGTH).value(0));
        }

        @Test
        void deletingWithoutAReviewIsANoOp() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);

            final ResultActions response = mockMvc.perform(
                delete(reviewUrl(DUNE_KEY)).header(AUTH_HEADER, BEARER_PREFIX + token));

            response.andExpect(status().isNoContent());
        }

        @Test
        void deletingWithoutAReviewLeavesTheExternalRatingIntact() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);
            jdbcTemplate.update(
                "UPDATE book SET average_rating = ?, rating_count = ? WHERE dedup_key = ?",
                new java.math.BigDecimal(EXTERNAL_RATING), EXTERNAL_RATING_COUNT, DUNE_KEY);

            mockMvc.perform(delete(reviewUrl(DUNE_KEY)).header(AUTH_HEADER, BEARER_PREFIX + token))
                .andExpect(status().isNoContent());

            final Book book = bookRepository.findByDedupKey(DUNE_KEY).orElseThrow();
            assertThat(book.getAverageRating()).isEqualByComparingTo(EXTERNAL_RATING);
            assertThat(book.getRatingCount()).isEqualTo(EXTERNAL_RATING_COUNT);
        }
    }

    @Nested
    @DisplayName("Community rating recompute")
    class CommunityRating {

        @Test
        void aUserRatingBecomesTheBooksAverage() throws Exception {
            final String darrowToken = registerAndLogin(DARROW, DARROW_EMAIL);
            final String goblinToken = registerAndLogin(GOBLIN, GOBLIN_EMAIL);
            putReview(darrowToken, DUNE_KEY, FIVE_STARS, null, null);
            putReview(goblinToken, DUNE_KEY, THREE_STARS, null, null);

            final Book book = bookRepository.findByDedupKey(DUNE_KEY).orElseThrow();

            assertThat(book.getAverageRating()).isEqualByComparingTo("4.00");
            assertThat(book.getRatingCount()).isEqualTo(2);
        }

        @Test
        void cascadeDeletingAReviewRecomputesTheBooksRating() throws Exception {
            final String darrowToken = registerAndLogin(DARROW, DARROW_EMAIL);
            final String goblinToken = registerAndLogin(GOBLIN, GOBLIN_EMAIL);
            putReview(darrowToken, DUNE_KEY, FIVE_STARS, null, null);
            putReview(goblinToken, DUNE_KEY, THREE_STARS, null, null);

            jdbcTemplate.update("DELETE FROM review WHERE rating = ?", THREE_STARS);

            final Book book = bookRepository.findByDedupKey(DUNE_KEY).orElseThrow();
            assertThat(book.getAverageRating()).isEqualByComparingTo("5.00");
            assertThat(book.getRatingCount()).isEqualTo(1);
        }
    }

    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private ResultActions putReview(final String token, final String key, final int rating,
        final @Nullable String title, final @Nullable String body) throws Exception {
        return mockMvc.perform(put(reviewUrl(key))
            .header(AUTH_HEADER, BEARER_PREFIX + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(reviewPayload(rating, title, body)));
    }

    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private ResultActions getBookReviews(final String key) throws Exception {
        return mockMvc.perform(get(bookReviewsUrl(key)));
    }

    private static String bookReviewsUrl(final String key) {
        return BOOKS_PATH + key + "/reviews";
    }

    private static String reviewUrl(final String key) {
        return BOOKS_PATH + key + "/reviews/me";
    }

    private void seedBook(final String key, final String title) {
        final Book book = new Book();
        book.setTitle(title);
        book.setDedupKey(key);
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
        return objectMapper.readTree(body).at("/data/accessToken").asString();
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

    private String reviewPayload(final int rating, final @Nullable String title,
        final @Nullable String body) {
        final ObjectNode node = objectMapper.createObjectNode();
        node.put("rating", rating);
        node.put("title", title);
        node.put("body", body);
        return objectMapper.writeValueAsString(node);
    }
}
