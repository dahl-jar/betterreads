package com.betterreads.search.service;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.betterreads.auth.ratelimit.RateLimitFilter;
import com.betterreads.support.ContainerizedTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The public search endpoint is rate limited per client, so a rapid burst past the configured
 * capacity is rejected with 429 instead of firing one Meilisearch query per hit.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "jwt.secret=integration-test-secret-must-be-at-least-256-bits-long-padding-padding",
    "jwt.issuer=betterreads-it",
    "auth.rate-limit.search-capacity=5",
    "auth.rate-limit.search-refill-tokens=1",
    "auth.rate-limit.search-refill-seconds=10"
})
class SearchRateLimitIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String SEARCH_URL = "/api/v1/search/books";

    private static final String QUERY = "dune";

    private static final int CAPACITY = 5;

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

    @Test
    @DisplayName("rejects a burst past capacity with 429 and Retry-After")
    void rejectsBurstPastCapacity() throws Exception {
        for (int i = 0; i < CAPACITY; i++) {
            mockMvc.perform(get(SEARCH_URL).param("q", QUERY))
                .andExpect(status().isOk());
        }

        mockMvc.perform(get(SEARCH_URL).param("q", QUERY))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"));
    }
}
