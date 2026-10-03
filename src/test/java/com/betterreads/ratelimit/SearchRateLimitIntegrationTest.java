package com.betterreads.ratelimit;

import static com.betterreads.ratelimit.RateLimitFixtures.RETRY_AFTER_HEADER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.betterreads.testsupport.ContainerizedTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Testcontainers;

/** A burst past the search-sized budget gets 429 per client. */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "auth.rate-limit.search-capacity=5",
    "auth.rate-limit.search-refill-tokens=1",
    "auth.rate-limit.search-refill-seconds=10"
})
class SearchRateLimitIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String SEARCH_URL = "/api/v1/search/books";

    private static final String REVIEW_COMMENTS_URL = "/api/v1/reviews/404/comments";

    private static final String COMMENT_BODY = "{\"body\":\"Break the chains\"}";

    private static final String RATING_URL = "/api/v1/books/OL45804W/reviews/me/rating";

    private static final String RATING_BODY = "{\"rating\":4}";

    private static final String SHELF_COUNTS_URL = "/api/v1/me/books/counts";

    private static final String QUERY = "dune";

    private static final int CAPACITY = 5;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = securedMockMvc();
        rateLimitFilter.reset();
    }

    @Test
    @DisplayName("rejects a burst past capacity with 429 and Retry-After")
    void rejectsBurstPastCapacity() throws Exception {
        for (int i = 0; i < CAPACITY; i++) {
            mockMvc.perform(get(SEARCH_URL).param("q", QUERY))
                .andExpect(status().isOk());
        }

        mockMvc.perform(get(SEARCH_URL).param("q", QUERY))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists(RETRY_AFTER_HEADER));
    }

    @Test
    void shouldRejectPublicReadBurstPastCapacity() throws Exception {
        for (int i = 0; i < CAPACITY; i++) {
            mockMvc.perform(get(REVIEW_COMMENTS_URL))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                    .isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS.value()));
        }

        mockMvc.perform(get(REVIEW_COMMENTS_URL))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists(RETRY_AFTER_HEADER));
    }

    @Test
    void shouldRejectCommentWriteBurstPastCapacity() throws Exception {
        for (int i = 0; i < CAPACITY; i++) {
            mockMvc.perform(post(REVIEW_COMMENTS_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(COMMENT_BODY))
                .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post(REVIEW_COMMENTS_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(COMMENT_BODY))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists(RETRY_AFTER_HEADER));
    }

    @Test
    void shouldRejectRatingBurstPastCapacity() throws Exception {
        spendRatingBucket();

        final ResultActions response = putRating();

        response
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists(RETRY_AFTER_HEADER));
    }

    @Test
    void shouldRejectShelfCountsBurstPastCapacity() throws Exception {
        for (int i = 0; i < CAPACITY; i++) {
            final ResultActions allowed = mockMvc.perform(get(SHELF_COUNTS_URL));
            allowed.andExpect(status().isUnauthorized());
        }

        final ResultActions response = mockMvc.perform(get(SHELF_COUNTS_URL));

        response
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists(RETRY_AFTER_HEADER));
    }

    @Test
    void shouldKeepShelfCountsOpenWhenTheRatingBucketIsSpent() throws Exception {
        spendRatingBucket();

        final ResultActions response = mockMvc.perform(get(SHELF_COUNTS_URL));

        response.andExpect(status().isUnauthorized());
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private void spendRatingBucket() throws Exception {
        for (int i = 0; i < CAPACITY; i++) {
            final ResultActions allowed = putRating();
            allowed.andExpect(status().isUnauthorized());
        }
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private ResultActions putRating() throws Exception {
        return mockMvc.perform(put(RATING_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(RATING_BODY));
    }
}
