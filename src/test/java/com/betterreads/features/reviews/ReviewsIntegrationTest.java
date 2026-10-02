package com.betterreads.features.reviews;

import com.betterreads.book.Book;
import com.betterreads.book.BookRepository;
import com.betterreads.testsupport.RegisteredUserTest;

import java.math.BigDecimal;

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
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.betterreads.testsupport.Books.DUNE_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Reviews and the community rating end to end, on real Postgres with real login tokens. */
@SpringBootTest
@Testcontainers
class ReviewsIntegrationTest extends RegisteredUserTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String AUTH_HEADER = "Authorization";

    private static final String BEARER_PREFIX = "Bearer ";

    private static final String DARROW = "darrow";

    private static final String DARROW_EMAIL = "darrow@example.com";

    private static final String GOBLIN = "goblin";

    private static final String GOBLIN_EMAIL = "goblin@example.com";

    private static final String REVIEW_TITLE = "A desert masterpiece";

    private static final String REVIEW_BODY = "The world-building carries the whole arc.";

    private static final int FIVE_STARS = 5;

    private static final int THREE_STARS = 3;

    private static final int ONE_STAR = 1;

    private static final int STAR_BUCKETS = 5;

    private static final double AVERAGE_OF_FIVE_AND_THREE = 4.0;

    private static final int RATING_BELOW_RANGE = 0;

    private static final int RATING_ABOVE_RANGE = 6;

    private static final int OVERLONG_BODY_LENGTH = 5001;

    private static final int OVERLONG_TITLE_LENGTH = 256;

    private static final String EXTERNAL_RATING = "4.20";

    private static final int EXTERNAL_RATING_COUNT = 99;

    private static final String JSON_RATING = "$.data.rating";

    private static final String JSON_TITLE = "$.data.title";

    private static final String JSON_BODY = "$.data.body";

    private static final String JSON_LENGTH = "$.data.length()";

    private static final String JSON_FIRST_RATING = "$.data[0].rating";

    private static final String JSON_FIRST_BOOK_KEY = "$.data[0].bookKey";

    private static final String JSON_COMMUNITY_AVERAGE = "$.data.average";

    private static final String JSON_COMMUNITY_COUNT = "$.data.count";

    private static final String JSON_DISTRIBUTION_LENGTH = "$.data.distribution.length()";

    private static final String JSON_FIRST_BUCKET_COUNT = "$.data.distribution[0].count";

    private static final String UNKNOWN_BOOK_KEY = "OL000000W";

    private static final String BOOKS_PATH = "/api/v1/books/";

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
        resetToDune();
    }

    @Nested
    @DisplayName("PUT /books/{key}/reviews/me")
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
        void blankTitleAndBodyAreStoredAsNoProse() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);

            final ResultActions response = putReview(token, DUNE_KEY, FIVE_STARS, "", "   ");

            response
                .andExpect(status().isOk())
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
        void shouldRejectAReviewWithoutARating() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);

            final ResultActions response = mockMvc.perform(put(reviewUrl(DUNE_KEY))
                .header(AUTH_HEADER, BEARER_PREFIX + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));

            response.andExpect(status().isBadRequest());
            final ResultActions remaining = getBookReviews(DUNE_KEY);
            remaining.andExpect(jsonPath(JSON_LENGTH).value(0));
        }

        @Test
        void shouldRejectAnOverlongTitle() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);
            final String overlong = "x".repeat(OVERLONG_TITLE_LENGTH);

            final ResultActions response = putReview(token, DUNE_KEY, FIVE_STARS, overlong, null);

            response.andExpect(status().isBadRequest());
            final ResultActions remaining = getBookReviews(DUNE_KEY);
            remaining.andExpect(jsonPath(JSON_LENGTH).value(0));
        }

        @Test
        void reviewingAnUnknownBookReturns404() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);

            final ResultActions response = putReview(token, UNKNOWN_BOOK_KEY, FIVE_STARS, null, null);

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
                .andExpect(jsonPath(JSON_FIRST_RATING).value(THREE_STARS))
                .andExpect(jsonPath(JSON_FIRST_BOOK_KEY).value(DUNE_KEY));
        }
    }

    @Nested
    @DisplayName("DELETE /books/{key}/reviews/me")
    class DeleteReview {

        @Test
        void aUserCanDeleteTheirOwnReview() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);
            putReview(token, DUNE_KEY, FIVE_STARS, REVIEW_TITLE, REVIEW_BODY);

            final ResultActions response = mockMvc.perform(
                delete(reviewUrl(DUNE_KEY)).header(AUTH_HEADER, BEARER_PREFIX + token));

            response.andExpect(status().isNoContent());
            final ResultActions remaining = getBookReviews(DUNE_KEY);
            remaining.andExpect(jsonPath(JSON_LENGTH).value(0));
        }

        @Test
        void deletingWithoutAReviewIsANoOp() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);

            final ResultActions response = mockMvc.perform(
                delete(reviewUrl(DUNE_KEY)).header(AUTH_HEADER, BEARER_PREFIX + token));

            response.andExpect(status().isNoContent());
        }
    }

    @Nested
    @DisplayName("Community rating recompute")
    class CommunityRating {

        @Test
        void aUserRatingLeavesTheSourceRatingUntouched() throws Exception {
            seedSourceRating(DUNE_KEY);
            final String darrowToken = registerAndLogin(DARROW, DARROW_EMAIL);
            putReview(darrowToken, DUNE_KEY, FIVE_STARS, null, null);

            final Book book = bookRepository.findByDedupKey(DUNE_KEY).orElseThrow();

            assertThat(book.getAverageRating()).isEqualByComparingTo(EXTERNAL_RATING);
            assertThat(book.getRatingCount()).isEqualTo(EXTERNAL_RATING_COUNT);
        }

        @Test
        void aReviewDeletedOutsideTheApiRecomputesTheCommunityRating() throws Exception {
            rateDuneFiveAndThree();

            jdbcTemplate.update("DELETE FROM review WHERE rating = ?", THREE_STARS);

            final Book book = bookRepository.findByDedupKey(DUNE_KEY).orElseThrow();
            assertThat(book.getCommunityAverage()).isEqualByComparingTo("5.00");
            assertThat(book.getCommunityCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("GET /books/{key}/community-rating")
    class CommunityRatingEndpoint {

        @Test
        void reportsTheAverageCountAndPerStarBreakdown() throws Exception {
            rateDuneFiveAndThree();

            final ResultActions response = getCommunityRating(DUNE_KEY);

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_COMMUNITY_AVERAGE).value(AVERAGE_OF_FIVE_AND_THREE))
                .andExpect(jsonPath(JSON_COMMUNITY_COUNT).value(2))
                .andExpect(jsonPath("$.data.distribution[0].star").value(FIVE_STARS))
                .andExpect(jsonPath(JSON_FIRST_BUCKET_COUNT).value(1))
                .andExpect(jsonPath("$.data.distribution[2].star").value(THREE_STARS))
                .andExpect(jsonPath("$.data.distribution[2].count").value(1))
                .andExpect(jsonPath("$.data.distribution[1].count").value(0))
                .andExpect(jsonPath("$.data.distribution[4].star").value(ONE_STAR));
        }

        @Test
        void anUnratedBookReportsNoAverageAndEmptyBuckets() throws Exception {
            final ResultActions response = getCommunityRating(DUNE_KEY);

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_COMMUNITY_AVERAGE).doesNotExist())
                .andExpect(jsonPath(JSON_COMMUNITY_COUNT).value(0))
                .andExpect(jsonPath(JSON_DISTRIBUTION_LENGTH).value(STAR_BUCKETS))
                .andExpect(jsonPath(JSON_FIRST_BUCKET_COUNT).value(0));
        }

        @Test
        void anUnknownBookReturns404() throws Exception {
            final ResultActions response = getCommunityRating(UNKNOWN_BOOK_KEY);

            response.andExpect(status().isNotFound());
        }
    }

    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private void rateDuneFiveAndThree() throws Exception {
        putReview(registerAndLogin(DARROW, DARROW_EMAIL), DUNE_KEY, FIVE_STARS, null, null);
        putReview(registerAndLogin(GOBLIN, GOBLIN_EMAIL), DUNE_KEY, THREE_STARS, null, null);
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private ResultActions putReview(final String token, final String key, final int rating,
        final @Nullable String title, final @Nullable String body) throws Exception {
        return mockMvc.perform(put(reviewUrl(key))
            .header(AUTH_HEADER, BEARER_PREFIX + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(reviewPayload(rating, title, body)));
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private ResultActions getBookReviews(final String key) throws Exception {
        return mockMvc.perform(get(bookReviewsUrl(key)));
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private ResultActions getCommunityRating(final String key) throws Exception {
        return mockMvc.perform(get(BOOKS_PATH + key + "/community-rating"));
    }

    private static String bookReviewsUrl(final String key) {
        return BOOKS_PATH + key + "/reviews";
    }

    private static String reviewUrl(final String key) {
        return BOOKS_PATH + key + "/reviews/me";
    }

    private void seedSourceRating(final String key) {
        jdbcTemplate.update(
            "UPDATE book SET average_rating = ?, rating_count = ? WHERE dedup_key = ?",
            new BigDecimal(EXTERNAL_RATING), EXTERNAL_RATING_COUNT, key);
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
