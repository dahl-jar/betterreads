package com.betterreads.features.search;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

final class MeilisearchServer {

    private static final String MASTER_KEY = "testMasterKey1234567890";

    private static final int PORT = 7700;

    private static final GenericContainer<?> CONTAINER = new GenericContainer<>(
            DockerImageName.parse("getmeili/meilisearch:v1.11"))
        .withExposedPorts(PORT)
        .withEnv("MEILI_MASTER_KEY", MASTER_KEY)
        .withEnv("MEILI_NO_ANALYTICS", "true")
        .withEnv("MEILI_ENV", "development");

    static {
        CONTAINER.start();
    }

    private MeilisearchServer() {
    }

    static void register(final DynamicPropertyRegistry registry, final String indexName) {
        registry.add("meilisearch.host",
            () -> "http://" + CONTAINER.getHost() + ":" + CONTAINER.getMappedPort(PORT));
        registry.add("meilisearch.master-key", () -> MASTER_KEY);
        registry.add("meilisearch.index-name", () -> indexName);
    }

    static void addToIndex(final Client client, final String indexName, final String json) {
        final Index index = client.index(indexName);
        index.waitForTask(index.addDocuments(json, BookSearchDocument.PRIMARY_KEY).getTaskUid());
    }

    static void removeFromIndex(final Client client, final String indexName, final String bookId) {
        final Index index = client.index(indexName);
        index.waitForTask(index.deleteDocument(bookId).getTaskUid());
    }
}
