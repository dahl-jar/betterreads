package com.betterreads.config;

import com.betterreads.support.ContainerizedTest;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the generated OpenAPI document shows the runtime {@code data}/{@code meta} shape, so
 * Swagger and generated clients match the wire format the response advice produces.
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

    private static final Path SPEC_FILE = Path.of("openapi.yaml");

    private static final boolean WRITE_SPEC = Boolean.getBoolean("openapi.write");

    private static final String BOOK_DETAIL_200 =
        "$.paths.['/api/v1/books/{key}'].get.responses.200.content.['*/*'].schema";

    private static final String SEARCH_200 =
        "$.paths.['/api/v1/search/books'].get.responses.200.content.['*/*'].schema";

    private static final String EVENT_STREAM_SCHEMA_REF = ".['text/event-stream'].schema.$ref";

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

    @Test
    void shouldMatchCommittedSpec() throws Exception {
        final String generated = MockMvcBuilders.webAppContextSetup(webApplicationContext).build()
            .perform(get(API_DOCS + ".yaml"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        if (WRITE_SPEC) {
            Files.writeString(SPEC_FILE, generated, StandardCharsets.UTF_8);
            return;
        }
        assertThat(Files.readString(SPEC_FILE, StandardCharsets.UTF_8))
            .as("openapi.yaml is stale, run ./gradlew openApiSpec and commit the result")
            .isEqualTo(generated);
    }

    @Test
    void shouldMarkPublicReadsAsAnonymous() throws Exception {
        mockMvc.perform(get(API_DOCS))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paths.['/api/v1/books/{key}/reviews'].get.security.length()").value(0))
            .andExpect(jsonPath("$.paths.['/api/v1/books/{key}/community-rating'].get.security.length()").value(0))
            .andExpect(jsonPath("$.paths.['/api/v1/books/{key}/comments'].get.security.length()").value(0))
            .andExpect(jsonPath("$.paths.['/api/v1/comments/{commentId}/replies'].get.security.length()").value(0))
            .andExpect(jsonPath("$.paths.['/healthz'].get.security.length()").value(0));
    }

    @Test
    void shouldDocumentStreamPayloadsAsTheirDocuments() throws Exception {
        mockMvc.perform(get(API_DOCS))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paths.['/api/v1/search/books/events'].get.responses.200.content"
                + EVENT_STREAM_SCHEMA_REF).value("#/components/schemas/BookSearchDocument"))
            .andExpect(jsonPath("$.paths.['/api/v1/books/{key}/events'].get.responses.200.content"
                + EVENT_STREAM_SCHEMA_REF).value("#/components/schemas/BookDetailResponse"));
    }

    @Test
    void shouldDocumentCoverAsBinaryImage() throws Exception {
        final String cover = "$.paths.['/api/v1/images/covers/{key}'].get.responses";

        mockMvc.perform(get(API_DOCS))
            .andExpect(status().isOk())
            .andExpect(jsonPath(cover + ".200.content.['image/jpeg'].schema.format").value("binary"))
            .andExpect(jsonPath(cover + ".200.content.['image/jpeg'].schema.properties").doesNotExist())
            .andExpect(jsonPath(cover + ".304").exists())
            .andExpect(jsonPath(cover + ".404").exists());
    }

    @Test
    void shouldDocumentBookDetailNotFound() throws Exception {
        mockMvc.perform(get(API_DOCS))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paths.['/api/v1/books/{key}'].get.responses.404").exists());
    }
}
