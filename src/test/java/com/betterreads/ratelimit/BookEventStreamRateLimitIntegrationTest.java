package com.betterreads.ratelimit;

import static com.betterreads.ratelimit.RateLimitFixtures.RETRY_AFTER_HEADER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.betterreads.testsupport.ContainerizedTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The public SSE event-stream endpoint has its own per-client bucket, so one caller cannot open
 * unlimited streams and exhaust the global open-stream cap for everyone else.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "auth.rate-limit.event-stream-capacity=3",
    "auth.rate-limit.event-stream-refill-tokens=1",
    "auth.rate-limit.event-stream-refill-seconds=30"
})
class BookEventStreamRateLimitIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String EVENTS_URL = "/api/v1/books/9780000000404/events";

    private static final int CAPACITY = 3;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = securedMockMvc();
        rateLimitFilter.reset();
    }

    @Test
    void shouldRejectStreamBurstPastCapacity() throws Exception {
        for (int i = 0; i < CAPACITY; i++) {
            mockMvc.perform(get(EVENTS_URL))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                    .isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS.value()));
        }

        mockMvc.perform(get(EVENTS_URL))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists(RETRY_AFTER_HEADER));
    }
}
