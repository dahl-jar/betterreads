package com.betterreads.config;

import com.betterreads.support.ContainerizedTest;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the generated OpenAPI document shows the runtime {@code data}/{@code meta} envelope, so
 * Swagger and generated clients match the wire shape the response advice produces.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "jwt.secret=integration-test-secret-must-be-at-least-256-bits-long-padding-padding",
    "jwt.issuer=betterreads-it",
    "jwt.expiration-minutes=60",
    "jwt.refresh-expiration-days=30"
})
class OpenApiEnvelopeTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String API_DOCS = "/v3/api-docs";

    private static final String BOOK_DETAIL_200 =
        "$.paths.['/api/v1/books/{key}'].get.responses.200.content.['*/*'].schema";

    private static final String SEARCH_200 =
        "$.paths.['/api/v1/search/books'].get.responses.200.content.['*/*'].schema";

    private static final String HEALTHZ_200 =
        "$.paths.['/healthz'].get.responses.200.content.['*/*'].schema";

    private static final String DATA_PROPERTY = ".properties.data";

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
    void singleResourceResponseIsWrappedInData() throws Exception {
        mockMvc.perform(get(API_DOCS))
            .andExpect(status().isOk())
            .andExpect(jsonPath(BOOK_DETAIL_200 + DATA_PROPERTY).exists());
    }

    @Test
    void pagedResponseDocumentsDataArrayAndMeta() throws Exception {
        mockMvc.perform(get(API_DOCS))
            .andExpect(status().isOk())
            .andExpect(jsonPath(SEARCH_200 + ".properties.data.type").value("array"))
            .andExpect(jsonPath(SEARCH_200 + ".properties.meta").exists());
    }

    @Test
    void healthResponseIsNotWrapped() throws Exception {
        mockMvc.perform(get(API_DOCS))
            .andExpect(status().isOk())
            .andExpect(jsonPath(HEALTHZ_200 + ".$ref").value("#/components/schemas/HealthResponse"))
            .andExpect(jsonPath(HEALTHZ_200 + DATA_PROPERTY).doesNotExist());
    }
}
