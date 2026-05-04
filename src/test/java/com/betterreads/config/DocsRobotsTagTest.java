package com.betterreads.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Swagger UI and OpenAPI JSON are public for portfolio access, but should not show up in
 * search-engine indexes. The docs filter chain emits {@code X-Robots-Tag: noindex, nofollow},
 * while the API and health chains do not (so the actual API is not robots-tagged by mistake).
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "jwt.secret=integration-test-secret-must-be-at-least-256-bits-long-padding-padding",
    "jwt.issuer=betterreads-it",
    "jwt.expiration-minutes=60",
    "jwt.refresh-expiration-days=30"
})
class DocsRobotsTagTest {

    private static final String ROBOTS_HEADER = "X-Robots-Tag";

    private static final String EXPECTED_VALUE = "noindex, nofollow";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .addFilter(springSecurityFilterChain)
            .build();
    }

    @Test
    void openApiJsonIsTaggedNoindex() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(header().string(ROBOTS_HEADER, EXPECTED_VALUE));
    }

    @Test
    void swaggerUiIsTaggedNoindex() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
            .andExpect(status().isOk())
            .andExpect(header().string(ROBOTS_HEADER, EXPECTED_VALUE));
    }

    @Test
    void healthzIsNotTaggedNoindex() throws Exception {
        mockMvc.perform(get("/healthz"))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist(ROBOTS_HEADER));
    }
}
