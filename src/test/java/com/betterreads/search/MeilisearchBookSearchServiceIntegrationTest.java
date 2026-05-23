package com.betterreads.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.betterreads.search.service.BookSearchService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Integration test for {@link BookSearchService} against a real Meilisearch instance.
 *
 * <p>The container's master key is a deterministic test fixture, unrelated to
 * any production credential.
 *
 * <p>TODO(implementer): cover index, search (typo-tolerant), paging, and remove.
 */
@SpringBootTest
@Testcontainers
@Disabled("scaffold only: enable once MeilisearchBookSearchService bodies + Postgres testcontainer are wired")
class MeilisearchBookSearchServiceIntegrationTest {

    private static final String TEST_MASTER_KEY = "testMasterKey1234567890";
    private static final int MEILISEARCH_PORT = 7700;

    @Container
    static final GenericContainer<?> MEILISEARCH = new GenericContainer<>(
            DockerImageName.parse("getmeili/meilisearch:v1.11"))
        .withExposedPorts(MEILISEARCH_PORT)
        .withEnv("MEILI_MASTER_KEY", TEST_MASTER_KEY)
        .withEnv("MEILI_NO_ANALYTICS", "true")
        .withEnv("MEILI_ENV", "development");

    @Autowired
    private BookSearchService searchService;

    @DynamicPropertySource
    static void overrideProps(final DynamicPropertyRegistry registry) {
        registry.add("meilisearch.host",
            () -> "http://" + MEILISEARCH.getHost() + ":" + MEILISEARCH.getMappedPort(MEILISEARCH_PORT));
        registry.add("meilisearch.master-key", () -> TEST_MASTER_KEY);
        registry.add("meilisearch.index-name", () -> "books-test");
    }

    @Test
    void indexAndSearchReturnsExpectedHits() {
        assertThat(searchService).as("search service must be wired in").isNotNull();
    }
}
