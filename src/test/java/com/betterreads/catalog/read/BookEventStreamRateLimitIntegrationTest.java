package com.betterreads.catalog.read;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.betterreads.auth.ratelimit.RateLimitFilter;
import com.betterreads.support.ContainerizedTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
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
    "jwt.secret=integration-test-secret-must-be-at-least-256-bits-long-padding-padding",
    "jwt.issuer=betterreads-it",
    "auth.rate-limit.event-stream-capacity=3",
    "auth.rate-limit.event-stream-refill-tokens=1",
    "auth.rate-limit.event-stream-refill-seconds=30"
})
class BookEventStreamRateLimitIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String EVENTS_URL = "/api/v1/books/9780000000404/events";

    private static final String SEARCH_EVENTS_URL = "/api/v1/search/books/events";

    private static final String QUERY_PARAM = "q";

    private static final String QUERY = "dune";

    private static final String RETRY_AFTER = "Retry-After";

    private static final int CAPACITY = 3;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .addFilter(springSecurityFilterChain)
            .build();
        rateLimitFilter.reset();
    }

    @ParameterizedTest
    @ValueSource(strings = {EVENTS_URL, SEARCH_EVENTS_URL})
    void shouldRejectStreamBurstPastCapacity(final String url) throws Exception {
        for (int i = 0; i < CAPACITY; i++) {
            mockMvc.perform(get(url).param(QUERY_PARAM, QUERY));
        }

        mockMvc.perform(get(url).param(QUERY_PARAM, QUERY))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists(RETRY_AFTER));
    }
}
