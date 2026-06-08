package com.betterreads.comments;

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
 * Verifies comments end-to-end: comment on a book, comment on a review, one level of replies with a
 * reply count, paged listing, delete own, and the ownership and one-level-depth rules.
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
class CommentsIntegrationTest extends ContainerizedTest {

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
        jdbcTemplate.update("DELETE FROM comment");
        jdbcTemplate.update("DELETE FROM review");
        jdbcTemplate.update("DELETE FROM book_author");
        jdbcTemplate.update("DELETE FROM book");
        jdbcTemplate.update("DELETE FROM app_user");
        rateLimitFilter.reset();
        seedBook(DUNE_KEY, DUNE_TITLE);
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

            final ResultActions response = postBookComment(token, "OL000000W", FIRST_COMMENT, null);

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
            final long parentId = commentId(postBookComment(token, DUNE_KEY, FIRST_COMMENT, null));
            postBookComment(token, DUNE_KEY, A_REPLY, parentId);

            final ResultActions list = getBookComments(DUNE_KEY);

            list
                .andExpect(jsonPath(JSON_TOTAL).value(1))
                .andExpect(jsonPath(JSON_FIRST_REPLY_COUNT).value(1));
        }

        @Test
        void repliesAreFetchedSeparately() throws Exception {
            final String token = registerAndLogin(DARROW, DARROW_EMAIL);
            final long parentId = commentId(postBookComment(token, DUNE_KEY, FIRST_COMMENT, null));
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
            final long parentId = commentId(postBookComment(token, DUNE_KEY, FIRST_COMMENT, null));
            final long replyId = commentId(postBookComment(token, DUNE_KEY, A_REPLY, parentId));

            final ResultActions response = postBookComment(token, DUNE_KEY, "nested", replyId);

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

            final ResultActions response = mockMvc.perform(
                post(REVIEWS_BASE + reviewId + COMMENTS_SUFFIX)
                    .header(AUTH_HEADER, BEARER_PREFIX + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(commentPayload(FIRST_COMMENT, null)));

            response
                .andExpect(status().isCreated())
                .andExpect(jsonPath(JSON_BODY).value(FIRST_COMMENT));
        }

        @Test
        void deletingAReviewDeletesItsComments() throws Exception {
            final String reviewerToken = registerAndLogin(DARROW, DARROW_EMAIL);
            final String commenterToken = registerAndLogin(GOBLIN, GOBLIN_EMAIL);
            final long reviewId = postReview(reviewerToken, DUNE_KEY);
            mockMvc.perform(post(REVIEWS_BASE + reviewId + COMMENTS_SUFFIX)
                    .header(AUTH_HEADER, BEARER_PREFIX + commenterToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(commentPayload(FIRST_COMMENT, null)))
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
            final long id = commentId(postBookComment(token, DUNE_KEY, FIRST_COMMENT, null));

            mockMvc.perform(delete(COMMENTS_BASE + id).header(AUTH_HEADER, BEARER_PREFIX + token))
                .andExpect(status().isNoContent());

            getBookComments(DUNE_KEY).andExpect(jsonPath(JSON_TOTAL).value(0));
        }

        @Test
        void deletingAnotherUsersCommentIsForbidden() throws Exception {
            final String darrowToken = registerAndLogin(DARROW, DARROW_EMAIL);
            final String goblinToken = registerAndLogin(GOBLIN, GOBLIN_EMAIL);
            final long id = commentId(postBookComment(darrowToken, DUNE_KEY, FIRST_COMMENT, null));

            final ResultActions response = mockMvc.perform(
                delete(COMMENTS_BASE + id).header(AUTH_HEADER, BEARER_PREFIX + goblinToken));

            response.andExpect(status().isForbidden());
        }
    }

    private static String bookCommentsUrl(final String key) {
        return BOOKS_BASE + key + COMMENTS_SUFFIX;
    }

    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private ResultActions postBookComment(final String token, final String key, final String body,
        final @Nullable Long parentId) throws Exception {
        return mockMvc.perform(post(bookCommentsUrl(key))
            .header(AUTH_HEADER, BEARER_PREFIX + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(commentPayload(body, parentId)));
    }

    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private ResultActions getBookComments(final String key) throws Exception {
        return mockMvc.perform(get(bookCommentsUrl(key)));
    }

    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private long commentId(final ResultActions created) throws Exception {
        final String body = created.andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at(ID_POINTER).asLong();
    }

    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private long postReview(final String token, final String key) throws Exception {
        final ObjectNode node = objectMapper.createObjectNode();
        node.put("rating", A_RATING);
        final MvcResult result = mockMvc.perform(put(BOOKS_BASE + key + OWN_REVIEW_SUFFIX)
                .header(AUTH_HEADER, BEARER_PREFIX + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(node)))
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at(ID_POINTER).asLong();
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

    private String commentPayload(final String body, final @Nullable Long parentId) {
        final ObjectNode node = objectMapper.createObjectNode();
        node.put(BODY_FIELD, body);
        if (parentId != null) {
            node.put("parentCommentId", parentId);
        }
        return objectMapper.writeValueAsString(node);
    }
}
