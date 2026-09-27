package com.betterreads.features.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.betterreads.testsupport.ContainerizedTest;
import com.meilisearch.sdk.Client;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MeilisearchIndexInitializerIntegrationTest extends ContainerizedTest {

    private static final String INDEX_NAME = "books-init-test";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    @Autowired
    private MeilisearchIndexInitializer initializer;

    @Autowired
    private Client client;

    @DynamicPropertySource
    static void meilisearchProps(final DynamicPropertyRegistry registry) {
        MeilisearchServer.register(registry, INDEX_NAME);
    }

    @Test
    @DisplayName("applies searchable settings to an existing index")
    void appliesSettingsToExistingIndex() {
        client.index(INDEX_NAME).waitForTask(client.createIndex(INDEX_NAME).getTaskUid());

        initializer.run(null);

        final String[] searchable = client.index(INDEX_NAME).getSettings().getSearchableAttributes();
        assertThat(searchable).contains("title", "authors", "subjects");
    }
}
