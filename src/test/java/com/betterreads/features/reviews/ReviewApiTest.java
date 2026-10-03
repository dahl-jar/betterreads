package com.betterreads.features.reviews;

import com.betterreads.testsupport.RegisteredUserTest;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static com.betterreads.testsupport.Accounts.AUTH_HEADER;
import static com.betterreads.testsupport.Accounts.BEARER_PREFIX;
import static com.betterreads.testsupport.Accounts.OTHER_USER;
import static com.betterreads.testsupport.Accounts.OTHER_USER_EMAIL;
import static com.betterreads.testsupport.Accounts.USER;
import static com.betterreads.testsupport.Accounts.USER_EMAIL;
import static com.betterreads.testsupport.Books.DUNE_KEY;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

abstract class ReviewApiTest extends RegisteredUserTest {

    protected static final String REVIEW_TITLE = "A desert masterpiece";

    protected static final String REVIEW_BODY = "The world-building carries the whole arc.";

    protected static final int FIVE_STARS = 5;

    protected static final int THREE_STARS = 3;

    protected static final String BELOW_ONE_PAYLOAD = "{\"rating\":0}";

    protected static final String ABOVE_FIVE_PAYLOAD = "{\"rating\":6}";

    protected static final String MISSING_RATING_PAYLOAD = "{}";

    protected static final String JSON_RATING = "$.data.rating";

    protected static final String JSON_TITLE = "$.data.title";

    protected static final String JSON_BODY = "$.data.body";

    protected static final String JSON_LENGTH = "$.data.length()";

    protected static final String JSON_FIRST_RATING = "$.data[0].rating";

    protected static final String JSON_COMMUNITY_AVERAGE = "$.data.average";

    protected static final String JSON_COMMUNITY_COUNT = "$.data.count";

    protected static final String JSON_FIRST_AUTHOR = "$.data[0].author";

    protected static final String JSON_FIRST_TITLE = "$.data[0].title";

    protected static final String UNKNOWN_BOOK_KEY = "OL000000W";

    private static final String BOOKS_PATH = "/api/v1/books/";

    private static final String OWN_REVIEWS_URL = "/api/v1/me/reviews";

    @Autowired
    protected ObjectMapper objectMapper;

    protected MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = securedMockMvc();
        resetToDune();
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    protected ResultActions putReview(final String token, final String key, final int rating,
        final @Nullable String title, final @Nullable String body) throws Exception {
        return mockMvc.perform(put(reviewUrl(key))
            .header(AUTH_HEADER, BEARER_PREFIX + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(reviewPayload(rating, title, body)));
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    protected String reviewDuneAsUser() throws Exception {
        final String token = registerAndLogin(USER, USER_EMAIL);
        putReview(token, DUNE_KEY, FIVE_STARS, REVIEW_TITLE, REVIEW_BODY);
        return token;
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    protected String reviewAsUserAndRateAsOtherUser() throws Exception {
        reviewDuneAsUser();
        final String otherUserToken = registerAndLogin(OTHER_USER, OTHER_USER_EMAIL);
        putReview(otherUserToken, DUNE_KEY, THREE_STARS, null, null);
        return otherUserToken;
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    protected ResultActions getOwnReviews(final String token) throws Exception {
        return mockMvc.perform(get(OWN_REVIEWS_URL).header(AUTH_HEADER, BEARER_PREFIX + token));
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    protected ResultActions getBookReviews(final String key) throws Exception {
        return mockMvc.perform(get(bookReviewsUrl(key)));
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    protected ResultActions getCommunityRating(final String key) throws Exception {
        return mockMvc.perform(get(BOOKS_PATH + key + "/community-rating"));
    }

    // PMD.SignatureDeclareThrowsException: ResultActions.andExpect declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    protected static void assertOneReviewTitled(final ResultActions response, final String title) throws Exception {
        response
            .andExpect(status().isOk())
            .andExpect(jsonPath(JSON_LENGTH).value(1))
            .andExpect(jsonPath(JSON_FIRST_TITLE).value(title));
    }

    // PMD.SignatureDeclareThrowsException: ResultActions.andExpect declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    protected static void assertOnlyUserListed(final ResultActions response) throws Exception {
        response
            .andExpect(status().isOk())
            .andExpect(jsonPath(JSON_LENGTH).value(1))
            .andExpect(jsonPath(JSON_FIRST_AUTHOR).value(USER));
    }

    protected static String reviewUrl(final String key) {
        return BOOKS_PATH + key + "/reviews/me";
    }

    protected String reviewPayload(final int rating, final @Nullable String title,
        final @Nullable String body) {
        final ObjectNode node = objectMapper.createObjectNode();
        node.put("rating", rating);
        node.put("title", title);
        node.put("body", body);
        return objectMapper.writeValueAsString(node);
    }

    private static String bookReviewsUrl(final String key) {
        return BOOKS_PATH + key + "/reviews";
    }
}
