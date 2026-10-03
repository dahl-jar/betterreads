package com.betterreads.features.reviews;

import com.betterreads.testsupport.ConcurrentCalls;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import tools.jackson.databind.node.ObjectNode;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.betterreads.testsupport.Accounts.AUTH_HEADER;
import static com.betterreads.testsupport.Accounts.BEARER_PREFIX;
import static com.betterreads.testsupport.Accounts.USER;
import static com.betterreads.testsupport.Accounts.USER_EMAIL;
import static com.betterreads.testsupport.Books.DUNE_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
class ReviewRatingIntegrationTest extends ReviewApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final int FOUR_STARS = 4;

    private static final double THREE_STAR_AVERAGE = 3.0;

    private static final String JSON_TOTAL = "$.meta.total";

    private static final String JSON_DETAIL = "$.detail";

    private static final String NO_BOOK_DETAIL = "No book with key ";

    private static final int PARALLEL_RATINGS = 8;

    @Nested
    @DisplayName("PUT /books/{key}/reviews/me/rating")
    class Rating {

        @Test
        void shouldKeepReviewTextWhenOnlyTheRatingChanges() throws Exception {
            final String token = reviewDuneAsUser();

            final ResultActions response = putRating(token, DUNE_KEY, ratingPayload(THREE_STARS));

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_RATING).value(THREE_STARS))
                .andExpect(jsonPath(JSON_TITLE).value(REVIEW_TITLE))
                .andExpect(jsonPath(JSON_BODY).value(REVIEW_BODY));
        }

        @Test
        void shouldCreateARatingOnlyReviewForAnUnratedBook() throws Exception {
            final String token = registerAndLogin(USER, USER_EMAIL);

            final ResultActions response = putRating(token, DUNE_KEY, ratingPayload(FOUR_STARS));

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_RATING).value(FOUR_STARS))
                .andExpect(jsonPath(JSON_TITLE).doesNotExist())
                .andExpect(jsonPath(JSON_BODY).doesNotExist());
            final ResultActions own = getOwnReviews(token);
            own
                .andExpect(jsonPath(JSON_LENGTH).value(1))
                .andExpect(jsonPath(JSON_FIRST_RATING).value(FOUR_STARS));
        }

        @Test
        void shouldRecomputeTheCommunityRatingAfterARating() throws Exception {
            final String token = reviewDuneAsUser();

            putRating(token, DUNE_KEY, ratingPayload(THREE_STARS));

            final ResultActions community = getCommunityRating(DUNE_KEY);
            community
                .andExpect(jsonPath(JSON_COMMUNITY_AVERAGE).value(THREE_STAR_AVERAGE))
                .andExpect(jsonPath(JSON_COMMUNITY_COUNT).value(1));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {BELOW_ONE_PAYLOAD, ABOVE_FIVE_PAYLOAD, MISSING_RATING_PAYLOAD})
        void shouldRejectAMissingOrOutOfRangeRating(final String payload) throws Exception {
            final String token = registerAndLogin(USER, USER_EMAIL);

            final ResultActions response = putRating(token, DUNE_KEY, payload);

            response.andExpect(status().isBadRequest());
            final ResultActions own = getOwnReviews(token);
            own.andExpect(jsonPath(JSON_LENGTH).value(0));
        }

        @Test
        void shouldKeepOneReviewWhenFirstRatingsArriveTogether() throws Exception {
            final String token = registerAndLogin(USER, USER_EMAIL);

            final List<Integer> statuses = rateConcurrently(token);

            assertThat(statuses).containsOnly(HttpStatus.OK.value());
            final ResultActions own = getOwnReviews(token);
            own.andExpect(jsonPath(JSON_LENGTH).value(1));
        }

        @Test
        void shouldReturn404WhenRatingAnUnknownBook() throws Exception {
            final String token = registerAndLogin(USER, USER_EMAIL);

            final ResultActions response = putRating(token, UNKNOWN_BOOK_KEY, ratingPayload(FOUR_STARS));

            response
                .andExpect(status().isNotFound())
                .andExpect(jsonPath(JSON_DETAIL).value(NO_BOOK_DETAIL + UNKNOWN_BOOK_KEY));
        }

        @Test
        void shouldRequireSignInToRateABook() throws Exception {
            final ResultActions response = mockMvc.perform(put(ratingUrl(DUNE_KEY))
                .contentType(MediaType.APPLICATION_JSON)
                .content(ratingPayload(FOUR_STARS)));

            response.andExpect(status().isUnauthorized());
            final ResultActions community = getCommunityRating(DUNE_KEY);
            community.andExpect(jsonPath(JSON_COMMUNITY_COUNT).value(0));
        }
    }

    @Nested
    @DisplayName("GET /books/{key}/reviews written reviews only")
    class ReviewList {

        @Test
        void shouldLeaveRatingOnlyReviewsOutOfABooksReviews() throws Exception {
            reviewAsUserAndRateAsOtherUser();

            final ResultActions response = getBookReviews(DUNE_KEY);

            assertOnlyUserListed(response);
            response.andExpect(jsonPath(JSON_TOTAL).value(1));
        }

        @Test
        void shouldListAReviewThatHasOnlyATitle() throws Exception {
            final String token = registerAndLogin(USER, USER_EMAIL);
            putReview(token, DUNE_KEY, FIVE_STARS, REVIEW_TITLE, null);

            final ResultActions response = getBookReviews(DUNE_KEY);

            assertOneReviewTitled(response, REVIEW_TITLE);
        }
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private ResultActions putRating(final String token, final String key, final String payload) throws Exception {
        return mockMvc.perform(put(ratingUrl(key))
            .header(AUTH_HEADER, BEARER_PREFIX + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload));
    }

    private static String ratingUrl(final String key) {
        return reviewUrl(key) + "/rating";
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private List<Integer> rateConcurrently(final String token) throws Exception {
        final String payload = ratingPayload(FOUR_STARS);
        return ConcurrentCalls.run(PARALLEL_RATINGS, () -> {
            final ResultActions rated = putRating(token, DUNE_KEY, payload);
            return rated.andReturn().getResponse().getStatus();
        });
    }

    private String ratingPayload(final int rating) {
        final ObjectNode node = objectMapper.createObjectNode();
        node.put("rating", rating);
        return objectMapper.writeValueAsString(node);
    }
}
