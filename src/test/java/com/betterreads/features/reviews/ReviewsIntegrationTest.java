package com.betterreads.features.reviews;

import com.betterreads.book.Author;
import com.betterreads.book.AuthorRepository;
import com.betterreads.book.Book;
import com.betterreads.book.BookRepository;
import com.betterreads.testsupport.Accounts;
import com.betterreads.testsupport.Books;
import com.betterreads.testsupport.Comments;

import java.math.BigDecimal;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.betterreads.testsupport.Accounts.OTHER_USER;
import static com.betterreads.testsupport.Accounts.OTHER_USER_EMAIL;
import static com.betterreads.testsupport.Accounts.USER;
import static com.betterreads.testsupport.Accounts.USER_EMAIL;
import static com.betterreads.testsupport.Books.DUNE_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Reviews and the community rating end to end, on real Postgres with real login tokens. */
@SpringBootTest
@Testcontainers
class ReviewsIntegrationTest extends ReviewApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final int ONE_STAR = 1;

    private static final int STAR_BUCKETS = 5;

    private static final double AVERAGE_OF_FIVE_AND_THREE = 4.0;

    private static final int OVERLONG_BODY_LENGTH = 5001;

    private static final int OVERLONG_TITLE_LENGTH = 256;

    private static final String EXTERNAL_RATING = "4.20";

    private static final int EXTERNAL_RATING_COUNT = 99;

    private static final String JSON_FIRST_BOOK_KEY = "$.data[0].bookKey";

    private static final String JSON_DISTRIBUTION_LENGTH = "$.data.distribution.length()";

    private static final String JSON_FIRST_BUCKET_COUNT = "$.data.distribution[0].count";

    private static final String DELETED_AUTHOR = "[deleted]";

    private static final String FIRST_COMMENT = "The ecology chapters are the best part.";

    private static final String SECOND_COMMENT = "The banquet scene carries it for me.";

    private static final String A_REPLY = "The appendix is worth reading too.";

    private static final String JSON_AUTHOR = "$.data.author";

    private static final String JSON_COMMENT_COUNT = "$.data.commentCount";

    private static final String JSON_SECOND_AUTHOR = "$.data[1].author";

    private static final String JSON_FIRST_COMMENT_COUNT = "$.data[0].commentCount";

    private static final String JSON_SECOND_COMMENT_COUNT = "$.data[1].commentCount";

    private static final String RECENT_REVIEWS_URL = "/api/v1/reviews/recent";

    private static final String LIMIT_PARAM = "limit";

    private static final String RED_RISING_KEY = "red-rising";

    private static final String RED_RISING_TITLE = "Red Rising";

    private static final String RED_RISING_AUTHOR = "Pierce Brown";

    private static final String FIRST_POSTED_AT = "2026-03-14T12:00:00Z";

    private static final String FIRST_POSTED_DATE = "2026-03-14";

    private static final String OTHER_USER_REVIEW_BODY = "Slow start, but the sandworms earn it.";

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private AuthorRepository authorRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Nested
    @DisplayName("PUT /books/{key}/reviews/me")
    class UpsertReview {

        @Test
        void postingAReviewReturnsItWithRatingTitleAndBody() throws Exception {
            final String token = registerAndLogin(USER, USER_EMAIL);

            final ResultActions response = putReview(token, DUNE_KEY, FIVE_STARS, REVIEW_TITLE, REVIEW_BODY);

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_RATING).value(FIVE_STARS))
                .andExpect(jsonPath(JSON_TITLE).value(REVIEW_TITLE))
                .andExpect(jsonPath(JSON_BODY).value(REVIEW_BODY));
        }

        @Test
        void ratingOnlyReviewHasNoProse() throws Exception {
            final String token = registerAndLogin(USER, USER_EMAIL);

            final ResultActions response = putReview(token, DUNE_KEY, FIVE_STARS, null, null);

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_RATING).value(FIVE_STARS))
                .andExpect(jsonPath(JSON_TITLE).doesNotExist())
                .andExpect(jsonPath(JSON_BODY).doesNotExist());
        }

        @Test
        void blankTitleAndBodyAreStoredAsNoProse() throws Exception {
            final String token = registerAndLogin(USER, USER_EMAIL);

            final ResultActions response = putReview(token, DUNE_KEY, FIVE_STARS, "", "   ");

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_TITLE).doesNotExist())
                .andExpect(jsonPath(JSON_BODY).doesNotExist());
        }

        @Test
        void secondSubmitEditsTheSameReview() throws Exception {
            final String token = registerAndLogin(USER, USER_EMAIL);
            putReview(token, DUNE_KEY, FIVE_STARS, REVIEW_TITLE, REVIEW_BODY);

            putReview(token, DUNE_KEY, THREE_STARS, "Changed my mind", "Pacing drags in the middle.");

            final ResultActions list = getBookReviews(DUNE_KEY);
            list
                .andExpect(jsonPath(JSON_LENGTH).value(1))
                .andExpect(jsonPath(JSON_FIRST_RATING).value(THREE_STARS));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {BELOW_ONE_PAYLOAD, ABOVE_FIVE_PAYLOAD, MISSING_RATING_PAYLOAD})
        void shouldRejectAReviewWithAMissingOrOutOfRangeRating(final String payload) throws Exception {
            final String token = registerAndLogin(USER, USER_EMAIL);

            final ResultActions response = mockMvc.perform(put(reviewUrl(DUNE_KEY))
                .header(Accounts.AUTH_HEADER, Accounts.BEARER_PREFIX + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload));

            response.andExpect(status().isBadRequest());
            final ResultActions own = getOwnReviews(token);
            own.andExpect(jsonPath(JSON_LENGTH).value(0));
        }

        @Test
        void overlongBodyIsRejected() throws Exception {
            final String token = registerAndLogin(USER, USER_EMAIL);
            final String overlong = "x".repeat(OVERLONG_BODY_LENGTH);

            final ResultActions response = putReview(token, DUNE_KEY, FIVE_STARS, null, overlong);

            response.andExpect(status().isBadRequest());
        }

        @Test
        void shouldRejectAnOverlongTitle() throws Exception {
            final String token = registerAndLogin(USER, USER_EMAIL);
            final String overlong = "x".repeat(OVERLONG_TITLE_LENGTH);

            final ResultActions response = putReview(token, DUNE_KEY, FIVE_STARS, overlong, null);

            response.andExpect(status().isBadRequest());
            final ResultActions remaining = getBookReviews(DUNE_KEY);
            remaining.andExpect(jsonPath(JSON_LENGTH).value(0));
        }

        @Test
        void reviewingAnUnknownBookReturns404() throws Exception {
            final String token = registerAndLogin(USER, USER_EMAIL);

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
            reviewDuneAsUser();

            final ResultActions response = getBookReviews(DUNE_KEY);

            assertOneReviewTitled(response, REVIEW_TITLE);
        }

        @Test
        void myReviewsReturnsOnlyTheCallersReviews() throws Exception {
            final String otherUserToken = reviewAsUserAndRateAsOtherUser();

            final ResultActions response = getOwnReviews(otherUserToken);

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_LENGTH).value(1))
                .andExpect(jsonPath(JSON_FIRST_RATING).value(THREE_STARS))
                .andExpect(jsonPath(JSON_FIRST_BOOK_KEY).value(DUNE_KEY))
                .andExpect(jsonPath(JSON_FIRST_AUTHOR).value(OTHER_USER));
        }
    }

    @Nested
    @DisplayName("DELETE /books/{key}/reviews/me")
    class DeleteReview {

        @Test
        void aUserCanDeleteTheirOwnReview() throws Exception {
            final String token = reviewDuneAsUser();

            final ResultActions response = mockMvc.perform(
                delete(reviewUrl(DUNE_KEY)).header(Accounts.AUTH_HEADER, Accounts.BEARER_PREFIX + token));

            response.andExpect(status().isNoContent());
            final ResultActions remaining = getBookReviews(DUNE_KEY);
            remaining.andExpect(jsonPath(JSON_LENGTH).value(0));
        }

        @Test
        void deletingWithoutAReviewIsANoOp() throws Exception {
            final String token = registerAndLogin(USER, USER_EMAIL);

            final ResultActions response = mockMvc.perform(
                delete(reviewUrl(DUNE_KEY)).header(Accounts.AUTH_HEADER, Accounts.BEARER_PREFIX + token));

            response.andExpect(status().isNoContent());
        }
    }

    @Nested
    @DisplayName("Community rating recompute")
    class CommunityRating {

        @Test
        void aUserRatingLeavesTheSourceRatingUntouched() throws Exception {
            seedSourceRating(DUNE_KEY);
            final String userToken = registerAndLogin(USER, USER_EMAIL);
            putReview(userToken, DUNE_KEY, FIVE_STARS, null, null);

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

    @Nested
    @DisplayName("Reviewer name and comment count on a review")
    class ReviewerAndComments {

        @Test
        void shouldNameEachReviewerOnABooksReviews() throws Exception {
            final String userToken = registerAndLogin(USER, USER_EMAIL);
            final String otherUserToken = registerAndLogin(OTHER_USER, OTHER_USER_EMAIL);
            putReview(userToken, DUNE_KEY, FIVE_STARS, REVIEW_TITLE, REVIEW_BODY);
            putReview(otherUserToken, DUNE_KEY, THREE_STARS, null, OTHER_USER_REVIEW_BODY);

            final ResultActions response = getBookReviews(DUNE_KEY);

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_FIRST_AUTHOR).value(OTHER_USER))
                .andExpect(jsonPath(JSON_SECOND_AUTHOR).value(USER));
        }

        @Test
        void shouldShowDeletedForAReviewerWhoseAccountIsDeleted() throws Exception {
            reviewDuneAsUser();
            Accounts.softDelete(jdbcTemplate, USER);

            final ResultActions response = getBookReviews(DUNE_KEY);

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_FIRST_AUTHOR).value(DELETED_AUTHOR));
        }

        @Test
        void shouldCountOnlyTopLevelCommentsOnAReview() throws Exception {
            final String token = registerAndLogin(USER, USER_EMAIL);
            final long reviewId = postReview(token, DUNE_KEY, FIVE_STARS, REVIEW_TITLE, REVIEW_BODY);
            final long parentId = commentOnReview(token, reviewId, FIRST_COMMENT, null);
            commentOnReview(token, reviewId, SECOND_COMMENT, null);
            commentOnReview(token, reviewId, A_REPLY, parentId);

            final ResultActions response = getBookReviews(DUNE_KEY);

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_FIRST_COMMENT_COUNT).value(2));
        }

        @Test
        void shouldReportZeroCommentsOnAnUncommentedReview() throws Exception {
            reviewDuneAsUser();

            final ResultActions response = getBookReviews(DUNE_KEY);

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_FIRST_COMMENT_COUNT).value(0));
        }

        @Test
        void shouldNotCountCommentsOnABookWithTheSameId() throws Exception {
            final String token = registerAndLogin(USER, USER_EMAIL);
            final long reviewId = postReview(token, DUNE_KEY, FIVE_STARS, REVIEW_TITLE, REVIEW_BODY);
            commentOnReview(token, reviewId, FIRST_COMMENT, null);
            final int bookComments = jdbcTemplate.update(
                "INSERT INTO comment (user_id, target_type, target_id, body) "
                    + "SELECT user_id, 'BOOK', ?, ? FROM app_user WHERE username = ?",
                reviewId, SECOND_COMMENT, USER);
            assertThat(bookComments).isOne();

            final ResultActions response = getBookReviews(DUNE_KEY);

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_FIRST_COMMENT_COUNT).value(1));
        }

        @Test
        void shouldKeepCommentCountsApartPerReview() throws Exception {
            final String userToken = registerAndLogin(USER, USER_EMAIL);
            final String otherUserToken = registerAndLogin(OTHER_USER, OTHER_USER_EMAIL);
            final long userReviewId = postReview(userToken, DUNE_KEY, FIVE_STARS, REVIEW_TITLE, REVIEW_BODY);
            final long otherUserReviewId =
                postReview(otherUserToken, DUNE_KEY, THREE_STARS, null, OTHER_USER_REVIEW_BODY);
            commentOnReview(otherUserToken, userReviewId, FIRST_COMMENT, null);
            commentOnReview(otherUserToken, userReviewId, SECOND_COMMENT, null);
            commentOnReview(userToken, otherUserReviewId, FIRST_COMMENT, null);

            final ResultActions response = getBookReviews(DUNE_KEY);

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_FIRST_AUTHOR).value(OTHER_USER))
                .andExpect(jsonPath(JSON_FIRST_COMMENT_COUNT).value(1))
                .andExpect(jsonPath(JSON_SECOND_COMMENT_COUNT).value(2));
        }

        @Test
        void shouldReturnTheListedFieldsWhenEditingAReview() throws Exception {
            final String userToken = registerAndLogin(USER, USER_EMAIL);
            final String otherUserToken = registerAndLogin(OTHER_USER, OTHER_USER_EMAIL);
            final long reviewId = postReview(userToken, DUNE_KEY, FIVE_STARS, REVIEW_TITLE, REVIEW_BODY);
            commentOnReview(otherUserToken, reviewId, FIRST_COMMENT, null);

            final ResultActions response = putReview(userToken, DUNE_KEY, THREE_STARS, null, null);

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_AUTHOR).value(USER))
                .andExpect(jsonPath(JSON_COMMENT_COUNT).value(1));
        }
    }

    @Nested
    @DisplayName("GET /reviews/recent")
    class RecentReviews {

        @Test
        void shouldListARecentReviewWithItsBook() throws Exception {
            final Author unsavedAuthor = Books.author(RED_RISING_AUTHOR);
            final Author pierceBrown = authorRepository.save(unsavedAuthor);
            final Book redRising = Books.promoted(RED_RISING_KEY, RED_RISING_TITLE, pierceBrown);
            bookRepository.save(redRising);
            final String token = registerAndLogin(USER, USER_EMAIL);
            final long reviewId = postReview(token, RED_RISING_KEY, FIVE_STARS, REVIEW_TITLE, REVIEW_BODY);
            jdbcTemplate.update("UPDATE review SET created_at = ?::timestamptz", FIRST_POSTED_AT);

            final ResultActions response = getRecentReviews();

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_LENGTH).value(1))
                .andExpect(jsonPath("$.data[0].id").value(reviewId))
                .andExpect(jsonPath(JSON_FIRST_AUTHOR).value(USER))
                .andExpect(jsonPath(JSON_FIRST_RATING).value(FIVE_STARS))
                .andExpect(jsonPath(JSON_FIRST_TITLE).value(REVIEW_TITLE))
                .andExpect(jsonPath("$.data[0].body").value(REVIEW_BODY))
                .andExpect(jsonPath("$.data[0].createdAt").value(FIRST_POSTED_DATE))
                .andExpect(jsonPath("$.data[0].book.key").value(RED_RISING_KEY))
                .andExpect(jsonPath("$.data[0].book.title").value(RED_RISING_TITLE))
                .andExpect(jsonPath("$.data[0].book.authors[0]").value(RED_RISING_AUTHOR))
                .andExpect(jsonPath("$.data[0].book.coverUrl")
                    .value(containsString("/api/v1/images/covers/" + RED_RISING_KEY)));
        }

        @Test
        void shouldOrderRecentReviewsByFirstPostedDate() throws Exception {
            final String userToken = reviewDuneAsUserAndOtherUser();
            putReview(userToken, DUNE_KEY, ONE_STAR, REVIEW_TITLE, REVIEW_BODY);

            final ResultActions response = getRecentReviews();

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_FIRST_AUTHOR).value(OTHER_USER))
                .andExpect(jsonPath(JSON_SECOND_AUTHOR).value(USER));
        }

        @Test
        void shouldLeaveOutRatingOnlyReviews() throws Exception {
            reviewAsUserAndRateAsOtherUser();

            final ResultActions response = getRecentReviews();

            assertOnlyUserListed(response);
        }

        @Test
        void shouldLeaveOutReviewsByDeletedAccounts() throws Exception {
            reviewDuneAsUserAndOtherUser();
            Accounts.softDelete(jdbcTemplate, OTHER_USER);

            final ResultActions response = getRecentReviews();

            assertOnlyUserListed(response);
        }

        @Test
        void shouldReturnNoMoreReviewsThanTheLimit() throws Exception {
            reviewDuneAsUserAndOtherUser();
            final String thirdUserToken = registerAndLogin(Accounts.THIRD_USER, Accounts.THIRD_USER_EMAIL);
            putReview(thirdUserToken, DUNE_KEY, ONE_STAR, REVIEW_TITLE, REVIEW_BODY);

            final ResultActions response = mockMvc.perform(get(RECENT_REVIEWS_URL).param(LIMIT_PARAM, "2"));

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_LENGTH).value(2));
        }

        @Test
        void shouldRejectARecentLimitOverTheCap() throws Exception {
            final ResultActions response = mockMvc.perform(get(RECENT_REVIEWS_URL).param(LIMIT_PARAM, "21"));

            response.andExpect(status().isBadRequest());
        }

        @Test
        void shouldRejectAZeroRecentLimit() throws Exception {
            final ResultActions response = mockMvc.perform(get(RECENT_REVIEWS_URL).param(LIMIT_PARAM, "0"));

            response.andExpect(status().isBadRequest());
        }
    }

    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private long commentOnReview(final String token, final long reviewId, final String body,
        final @Nullable Long parentId) throws Exception {
        final ResultActions created = mockMvc.perform(
            Comments.request(objectMapper, token, Comments.reviewCommentsUrl(reviewId), body, parentId));
        created.andExpect(status().isCreated());
        return idOf(created);
    }

    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private long postReview(final String token, final String key, final int rating,
        final @Nullable String title, final @Nullable String body) throws Exception {
        final ResultActions created = putReview(token, key, rating, title, body);
        created.andExpect(status().isOk());
        return idOf(created);
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private String reviewDuneAsUserAndOtherUser() throws Exception {
        final String userToken = reviewDuneAsUser();
        final String otherUserToken = registerAndLogin(OTHER_USER, OTHER_USER_EMAIL);
        putReview(otherUserToken, DUNE_KEY, THREE_STARS, REVIEW_TITLE, REVIEW_BODY);
        return userToken;
    }

    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private ResultActions getRecentReviews() throws Exception {
        return mockMvc.perform(get(RECENT_REVIEWS_URL));
    }

    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private void rateDuneFiveAndThree() throws Exception {
        final String userToken = registerAndLogin(USER, USER_EMAIL);
        final String otherUserToken = registerAndLogin(OTHER_USER, OTHER_USER_EMAIL);
        postReview(userToken, DUNE_KEY, FIVE_STARS, null, null);
        postReview(otherUserToken, DUNE_KEY, THREE_STARS, null, null);
    }

    private void seedSourceRating(final String key) {
        jdbcTemplate.update(
            "UPDATE book SET average_rating = ?, rating_count = ? WHERE dedup_key = ?",
            new BigDecimal(EXTERNAL_RATING), EXTERNAL_RATING_COUNT, key);
    }
}
