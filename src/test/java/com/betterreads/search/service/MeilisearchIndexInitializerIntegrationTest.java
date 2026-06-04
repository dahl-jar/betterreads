package com.betterreads.search.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.betterreads.support.ContainerizedTest;
import com.meilisearch.sdk.Client;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The index initializer applies search settings even when the index already exists, so a restart
 * after first boot still reaches a freshly created or settings-less index.
 */
@SpringBootTest
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
    "jwt.secret=integration-test-secret-must-be-at-least-256-bits-long-padding-padding",
    "jwt.issuer=betterreads-it"
})
class MeilisearchIndexInitializerIntegrationTest extends ContainerizedTest {

    private static final String TEST_MASTER_KEY = "testMasterKey1234567890";

    private static final int MEILISEARCH_PORT = 7700;

    private static final String INDEX_NAME = "books-init-test";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    static final GenericContainer<?> MEILISEARCH = new GenericContainer<>(
            DockerImageName.parse("getmeili/meilisearch:v1.11"))
        .withExposedPorts(MEILISEARCH_PORT)
        .withEnv("MEILI_MASTER_KEY", TEST_MASTER_KEY)
        .withEnv("MEILI_NO_ANALYTICS", "true")
        .withEnv("MEILI_ENV", "development");

    static {
        MEILISEARCH.start();
    }

    @Autowired
    private MeilisearchIndexInitializer initializer;

    @Autowired
    private Client client;

    @DynamicPropertySource
    static void meilisearchProps(final DynamicPropertyRegistry registry) {
        registry.add("meilisearch.host",
            () -> "http://" + MEILISEARCH.getHost() + ":" + MEILISEARCH.getMappedPort(MEILISEARCH_PORT));
        registry.add("meilisearch.master-key", () -> TEST_MASTER_KEY);
        registry.add("meilisearch.index-name", () -> INDEX_NAME);
    }

    @Test
    @DisplayName("applies searchable settings when the index already exists")
    void appliesSettingsToExistingIndex() throws Exception {
        client.index(INDEX_NAME).waitForTask(client.createIndex(INDEX_NAME).getTaskUid());

        initializer.run(null);

        final List<String> searchable = client.index(INDEX_NAME).getSettings().getSearchableAttributes() == null
            ? List.of()
            : List.of(client.index(INDEX_NAME).getSettings().getSearchableAttributes());
        assertThat(searchable).contains("title", "authors", "subjects");
    }
}
