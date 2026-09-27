package com.betterreads.security;

import com.betterreads.testsupport.ContainerizedTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The docs are public but should stay out of search indexes, so only the docs chain sends
 * {@code X-Robots-Tag: noindex, nofollow}.
 */
@SpringBootTest
@Testcontainers
class DocsRobotsTagTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String ROBOTS_HEADER = "X-Robots-Tag";

    private static final String EXPECTED_VALUE = "noindex, nofollow";

    private static final String HEALTHZ = "/healthz";

    private static final String ISO_INSTANT_PATTERN = "\\d{4}-\\d{2}-\\d{2}T.*Z";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = securedMockMvc();
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
        mockMvc.perform(get(HEALTHZ))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist(ROBOTS_HEADER));
    }

    @Test
    void shouldReturnStatusUpFromHealthz() throws Exception {
        mockMvc.perform(get(HEALTHZ))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.service").value("betterreads"))
            .andExpect(jsonPath("$.timestamp", matchesPattern(ISO_INSTANT_PATTERN)));
    }
}
