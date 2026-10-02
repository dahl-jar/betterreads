package com.betterreads.ratelimit;

import static com.betterreads.ratelimit.RateLimitFixtures.RETRY_AFTER_HEADER;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.betterreads.testsupport.ContainerizedTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "auth.rate-limit.search-capacity=3",
    "auth.rate-limit.search-refill-tokens=1",
    "auth.rate-limit.search-refill-seconds=30",
    "auth.rate-limit.image-capacity=5",
    "auth.rate-limit.image-refill-tokens=1",
    "auth.rate-limit.image-refill-seconds=30"
})
class ImageRateLimitIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String UNKNOWN_KEY = "OL000000W";

    private static final String COVER_URL = "/api/v1/images/covers/" + UNKNOWN_KEY;

    private static final String BOOK_REVIEWS_URL = "/api/v1/books/" + UNKNOWN_KEY + "/reviews";

    private static final int PUBLIC_READ_CAPACITY = 3;

    private static final int IMAGE_CAPACITY = 5;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = securedMockMvc();
        rateLimitFilter.reset();
    }

    @Test
    void shouldServeCoversAfterThePublicReadBucketIsSpent() throws Exception {
        for (int i = 0; i < PUBLIC_READ_CAPACITY; i++) {
            mockMvc.perform(get(BOOK_REVIEWS_URL))
                .andExpect(status().isNotFound());
        }
        mockMvc.perform(get(BOOK_REVIEWS_URL))
            .andExpect(status().isTooManyRequests());

        final ResultActions cover = mockMvc.perform(get(COVER_URL));

        cover.andExpect(status().isNotFound());
    }

    @Test
    void shouldLimitCoversAtTheImageCapacity() throws Exception {
        for (int i = 0; i < IMAGE_CAPACITY; i++) {
            mockMvc.perform(get(COVER_URL))
                .andExpect(status().isNotFound());
        }

        final ResultActions overflow = mockMvc.perform(get(COVER_URL));

        overflow
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists(RETRY_AFTER_HEADER));
    }
}
