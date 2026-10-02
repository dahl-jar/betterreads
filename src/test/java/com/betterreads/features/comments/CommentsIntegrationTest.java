package com.betterreads.features.comments;

import java.io.UnsupportedEncodingException;

import com.betterreads.book.BookRepository;
import com.betterreads.testsupport.Books;
import com.betterreads.testsupport.RegisteredUserTest;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Comment endpoints end-to-end against Postgres. */
@SpringBootTest
@Testcontainers
class CommentsIntegrationTest extends RegisteredUserTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String AUTH_HEADER = "Authorization";

    private static final String BEARER_PREFIX = "Bearer ";

    private static final String MESSIAH_KEY = "OL893416W";

    private static final String MESSIAH_TITLE = "Dune Messiah";

    private static final int MAX_BODY_LENGTH = 5000;

    private static final String DARROW = "darrow";

    private static final String DARROW_EMAIL = "darrow@example.com";

    private static final String GOBLIN = "goblin";

    private static final String GOBLIN_EMAIL = "goblin@example.com";

    private static final String BODY_FIELD = "body";

    private static final String FIRST_COMMENT = "The desert ecology is the real protagonist.";

    private static final String A_REPLY = "Agreed, the spice trade is just set dressing.";

    private static final int A_RATING = 5;

    private static final int THREE_COMMENTS = 3;

    private static final String MIDDLE_COMMENT = "middle";

    private static final String OFFSET_PARAM = "offset";

    private static final String LIMIT_PARAM = "limit";

    private static final String REVIEWS_BASE = "/api/v1/reviews/";

    private static final String OWN_REVIEW_SUFFIX = "/reviews/me";

    private static final String BOOKS_BASE = "/api/v1/books/";

    private static final String COMMENTS_SUFFIX = "/comments";

    private static final String COMMENTS_BASE = "/api/v1/comments/";

    private static final String ID_POINTER = "/data/id";

    private static final String JSON_BODY = "$.data.body";

    private static final String JSON_TOTAL = "$.meta.total";

    private static final String JSON_FIRST_BODY = "$.data[0].body";

    private static final String JSON_FIRST_REPLY_COUNT = "$.data[0].replyCount";

    private static final long UNKNOWN_COMMENT_ID = 9_999_999L;

    private static final long UNKNOWN_REVIEW_ID = 9_999_999L;

    private static final String UNKNOWN_BOOK_KEY = "OL000000W";

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
        jdbcTemplate.update("DELETE FROM comment");
        resetToDune();
    }

    @Nested
    @DisplayName("POST/GET /books/{key}/comments")
    class BookComments {

        @Test
        void postingACommentReturnsItWithBody() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);

            final ResultActions response = postBookComment(token, DUNE_KEY, FIRST_COMMENT, null);

            response
                .andExpect(status().isCreated())
                .andExpect(jsonPath(JSON_BODY).value(FIRST_COMMENT))
                .andExpect(jsonPath("$.data.author").value(DARROW));
        }

        @Test
        void aBooksCommentsArePublicAndPaged() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);
            postBookComment(token, DUNE_KEY, FIRST_COMMENT, null);

            final ResultActions list = getBookComments(DUNE_KEY);

            list
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_TOTAL).value(1))
                .andExpect(jsonPath(JSON_FIRST_BODY).value(FIRST_COMMENT))
                .andExpect(jsonPath(JSON_FIRST_REPLY_COUNT).value(0));
        }

        @Test
        void anOffsetNotAlignedToTheLimitReturnsTheExactRow() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);
            postBookComment(token, DUNE_KEY, "oldest", null);
            postBookComment(token, DUNE_KEY, MIDDLE_COMMENT, null);
            postBookComment(token, DUNE_KEY, "newest", null);

            final ResultActions page = mockMvc.perform(
                get(bookCommentsUrl(DUNE_KEY)).param(OFFSET_PARAM, "1").param(LIMIT_PARAM, "1"));

            page
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_TOTAL).value(THREE_COMMENTS))
                .andExpect(jsonPath(JSON_FIRST_BODY).value(MIDDLE_COMMENT));
        }

        @Test
        void commentingOnAnUnknownBookReturns404() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);

            final ResultActions response = postBookComment(token, UNKNOWN_BOOK_KEY, FIRST_COMMENT, null);

            response.andExpect(status().isNotFound());
        }

        @Test
        void shouldReturnNotFoundForListingUnknownBook() throws Exception {
            final ResultActions response = getBookComments(UNKNOWN_BOOK_KEY);

            response.andExpect(status().isNotFound());
        }

        @Test
        void unauthenticatedCommentReturns401() throws Exception {
            final ResultActions response = mockMvc.perform(
                post(bookCommentsUrl(DUNE_KEY))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(commentPayload(FIRST_COMMENT, null)));

            response.andExpect(status().isUnauthorized());
        }

        @Test
        void emptyBodyIsRejected() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);

            final ResultActions response = postBookComment(token, DUNE_KEY, "   ", null);

            response.andExpect(status().isBadRequest());
        }

        @Test
        void shouldRejectCommentOver5000Characters() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);
            final String overlong = "x".repeat(MAX_BODY_LENGTH + 1);

            final ResultActions response = postBookComment(token, DUNE_KEY, overlong, null);

            response.andExpect(status().isBadRequest());
        }

        @Test
        void nonPositiveLimitIsRejected() throws Exception {
            final ResultActions response = mockMvc.perform(
                get(bookCommentsUrl(DUNE_KEY)).param(LIMIT_PARAM, "0"));

            response.andExpect(status().isBadRequest());
        }

        @Test
        void negativeOffsetIsRejected() throws Exception {
            final ResultActions response = mockMvc.perform(
                get(bookCommentsUrl(DUNE_KEY)).param(OFFSET_PARAM, "-1"));

            response.andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Replies (one level)")
    class Replies {

        @Test
        void aReplyCountsTowardItsParentsReplyCount() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);
            final long parentId = idOf(postBookComment(token, DUNE_KEY, FIRST_COMMENT, null));
            postBookComment(token, DUNE_KEY, A_REPLY, parentId);

            final ResultActions list = getBookComments(DUNE_KEY);

            list
                .andExpect(jsonPath(JSON_TOTAL).value(1))
                .andExpect(jsonPath(JSON_FIRST_REPLY_COUNT).value(1));
        }

        @Test
        void repliesAreFetchedSeparately() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);
            final long parentId = idOf(postBookComment(token, DUNE_KEY, FIRST_COMMENT, null));
            postBookComment(token, DUNE_KEY, A_REPLY, parentId);

            final ResultActions replies = mockMvc.perform(
                get(COMMENTS_BASE + parentId + "/replies"));

            replies
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_TOTAL).value(1))
                .andExpect(jsonPath(JSON_FIRST_BODY).value(A_REPLY));
        }

        @Test
        void replyingToAReplyIsRejected() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);
            final long parentId = idOf(postBookComment(token, DUNE_KEY, FIRST_COMMENT, null));
            final long replyId = idOf(postBookComment(token, DUNE_KEY, A_REPLY, parentId));

            final ResultActions response = postBookComment(token, DUNE_KEY, "nested", replyId);

            response.andExpect(status().isBadRequest());
        }

        @Test
        void shouldReturnNotFoundForReplyToUnknownParent() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);

            final ResultActions response = postBookComment(token, DUNE_KEY, A_REPLY, UNKNOWN_COMMENT_ID);

            response.andExpect(status().isNotFound());
        }

        @Test
        void replyingToACommentOnAnotherTargetIsRejected() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);
            final long reviewId = postReview(token, DUNE_KEY);
            final long bookCommentId = idOf(postBookComment(token, DUNE_KEY, FIRST_COMMENT, null));

            final ResultActions response = postReviewComment(token, reviewId, A_REPLY, bookCommentId);

            response.andExpect(status().isBadRequest());
        }

        @Test
        void shouldRejectReplyWhenParentIsOnAnotherBook() throws Exception {
            Books.seedBook(bookRepository, MESSIAH_KEY, MESSIAH_TITLE);
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);
            final long parentId = idOf(postBookComment(token, DUNE_KEY, FIRST_COMMENT, null));

            final ResultActions response = postBookComment(token, MESSIAH_KEY, A_REPLY, parentId);

            response.andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Comments on a review")
    class ReviewComments {

        @Test
        void aCommentCanTargetAReview() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);
            final long reviewId = postReview(token, DUNE_KEY);

            final ResultActions response = postReviewComment(token, reviewId, FIRST_COMMENT, null);

            response
                .andExpect(status().isCreated())
                .andExpect(jsonPath(JSON_BODY).value(FIRST_COMMENT));
        }

        @Test
        void shouldReturnNotFoundForCommentOnUnknownReview() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);

            final ResultActions response = postReviewComment(token, UNKNOWN_REVIEW_ID, FIRST_COMMENT, null);

            response.andExpect(status().isNotFound());
        }

        @Test
        void shouldListCommentsOnReview() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);
            final long reviewId = postReview(token, DUNE_KEY);
            postReviewComment(token, reviewId, FIRST_COMMENT, null);

            final ResultActions list = mockMvc.perform(get(reviewCommentsUrl(reviewId)));

            list
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_TOTAL).value(1))
                .andExpect(jsonPath(JSON_FIRST_BODY).value(FIRST_COMMENT));
        }

        @Test
        void shouldReturnNotFoundForListingUnknownReview() throws Exception {
            final ResultActions response = mockMvc.perform(get(reviewCommentsUrl(UNKNOWN_REVIEW_ID)));

            response.andExpect(status().isNotFound());
        }

        @Test
        void deletingAReviewDeletesItsComments() throws Exception {
            final String reviewerToken = registerAndLogin(DARROW, DARROW_EMAIL);
            final String commenterToken = registerAndLogin(GOBLIN, GOBLIN_EMAIL);
            final long reviewId = postReview(reviewerToken, DUNE_KEY);
            postReviewComment(commenterToken, reviewId, FIRST_COMMENT, null)
                .andExpect(status().isCreated());

            mockMvc.perform(delete(BOOKS_BASE + DUNE_KEY + OWN_REVIEW_SUFFIX)
                    .header(AUTH_HEADER, BEARER_PREFIX + reviewerToken))
                .andExpect(status().isNoContent());

            final Long orphans = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM comment WHERE target_type = 'REVIEW' AND target_id = ?",
                Long.class, reviewId);
            assertThat(orphans).isZero();
        }
    }

    @Nested
    @DisplayName("DELETE /comments/{id}")
    class DeleteComment {

        @Test
        void anAuthorCanDeleteTheirOwnComment() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);
            final long id = idOf(postBookComment(token, DUNE_KEY, FIRST_COMMENT, null));

            mockMvc.perform(delete(COMMENTS_BASE + id).header(AUTH_HEADER, BEARER_PREFIX + token))
                .andExpect(status().isNoContent());

            getBookComments(DUNE_KEY).andExpect(jsonPath(JSON_TOTAL).value(0));
        }

        @Test
        void deletingAnotherUsersCommentIsForbidden() throws Exception {
            final String darrowToken = registerAndLogin(DARROW, DARROW_EMAIL);
            final String goblinToken = registerAndLogin(GOBLIN, GOBLIN_EMAIL);
            final long id = idOf(postBookComment(darrowToken, DUNE_KEY, FIRST_COMMENT, null));

            final ResultActions response = mockMvc.perform(
                delete(COMMENTS_BASE + id).header(AUTH_HEADER, BEARER_PREFIX + goblinToken));

            response.andExpect(status().isForbidden());
            getBookComments(DUNE_KEY).andExpect(jsonPath(JSON_TOTAL).value(1));
        }

        @Test
        void shouldReturnUnauthorizedWhenDeletingWithoutToken() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);
            final long id = idOf(postBookComment(token, DUNE_KEY, FIRST_COMMENT, null));

            final ResultActions response = mockMvc.perform(delete(COMMENTS_BASE + id));

            response.andExpect(status().isUnauthorized());
            getBookComments(DUNE_KEY).andExpect(jsonPath(JSON_TOTAL).value(1));
        }

        @Test
        void deletingAnUnknownCommentIsANoOp() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);

            final ResultActions response = mockMvc.perform(
                delete(COMMENTS_BASE + UNKNOWN_COMMENT_ID).header(AUTH_HEADER, BEARER_PREFIX + token));

            response.andExpect(status().isNoContent());
        }

        @Test
        void deletingACommentDeletesItsReplies() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);
            final long parentId = idOf(postBookComment(token, DUNE_KEY, FIRST_COMMENT, null));
            postBookComment(token, DUNE_KEY, A_REPLY, parentId);

            mockMvc.perform(delete(COMMENTS_BASE + parentId).header(AUTH_HEADER, BEARER_PREFIX + token))
                .andExpect(status().isNoContent());

            final Long remaining = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM comment", Long.class);
            assertThat(remaining).isZero();
        }
    }

    private static String bookCommentsUrl(final String key) {
        return BOOKS_BASE + key + COMMENTS_SUFFIX;
    }

    private static String reviewCommentsUrl(final long reviewId) {
        return REVIEWS_BASE + reviewId + COMMENTS_SUFFIX;
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private ResultActions postBookComment(final String token, final String key, final String body,
        final @Nullable Long parentId) throws Exception {
        return postComment(token, bookCommentsUrl(key), body, parentId);
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private ResultActions postReviewComment(final String token, final long reviewId, final String body,
        final @Nullable Long parentId) throws Exception {
        return postComment(token, reviewCommentsUrl(reviewId), body, parentId);
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private ResultActions postComment(final String token, final String url, final String body,
        final @Nullable Long parentId) throws Exception {
        return mockMvc.perform(post(url)
            .header(AUTH_HEADER, BEARER_PREFIX + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(commentPayload(body, parentId)));
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private ResultActions getBookComments(final String key) throws Exception {
        return mockMvc.perform(get(bookCommentsUrl(key)));
    }

    private long idOf(final ResultActions created) throws UnsupportedEncodingException {
        final String body = created.andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at(ID_POINTER).asLong();
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private long postReview(final String token, final String key) throws Exception {
        final ObjectNode node = objectMapper.createObjectNode();
        node.put("rating", A_RATING);
        final ResultActions created = mockMvc.perform(put(BOOKS_BASE + key + OWN_REVIEW_SUFFIX)
            .header(AUTH_HEADER, BEARER_PREFIX + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(node)));
        return idOf(created);
    }

    private String commentPayload(final String body, final @Nullable Long parentId) {
        final ObjectNode node = objectMapper.createObjectNode();
        node.put(BODY_FIELD, body);
        if (parentId != null) {
            node.put("parentCommentId", parentId);
        }
        return objectMapper.writeValueAsString(node);
    }
}
