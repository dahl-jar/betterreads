package com.betterreads.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.betterreads.search.dto.BookSearchDocument;
import com.betterreads.search.dto.BookSearchResult;
import com.betterreads.search.service.BookSearchService;
import com.betterreads.support.ContainerizedTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Identical rapid searches reuse one Meilisearch call: a repeated query returns the cached result
 * even after the index changes, until the entry is evicted.
 */
@SpringBootTest
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
    "jwt.secret=integration-test-secret-must-be-at-least-256-bits-long-padding-padding",
    "jwt.issuer=betterreads-it"
})
class SearchQueryCacheIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String TEST_MASTER_KEY = "testMasterKey1234567890";

    private static final int MEILISEARCH_PORT = 7700;

    private static final int FULL_PAGE = 20;

    private static final int FIRST_YEAR = 1965;

    private static final double POPULARITY = 9.0;

    private static final String DUNE_TITLE = "Dune";

    private static final String DUNE_QUERY = "dune";

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
    private BookSearchService searchService;

    @Autowired
    private CacheManager cacheManager;

    @DynamicPropertySource
    static void meilisearchProps(final DynamicPropertyRegistry registry) {
        registry.add("meilisearch.host",
            () -> "http://" + MEILISEARCH.getHost() + ":" + MEILISEARCH.getMappedPort(MEILISEARCH_PORT));
        registry.add("meilisearch.master-key", () -> TEST_MASTER_KEY);
        registry.add("meilisearch.index-name", () -> "books-querycache-test");
    }

    @BeforeEach
    void clearCache() {
        cacheManager.getCache("searchResults").clear();
    }

    @Test
    @DisplayName("a repeated identical query reuses the first result")
    void cachesIdenticalQuery() {
        searchService.index(List.of(doc("1", DUNE_TITLE)));
        final BookSearchResult first = searchService.search(DUNE_QUERY, 0, FULL_PAGE);

        searchService.remove("1");
        final BookSearchResult repeat = searchService.search(DUNE_QUERY, 0, FULL_PAGE);

        assertThat(first.hits()).extracting(BookSearchDocument::bookId).containsExactly("1");
        assertThat(repeat.hits()).extracting(BookSearchDocument::bookId).containsExactly("1");
    }

    @Test
    @DisplayName("a different page is a separate cache entry")
    void differentPageMisses() {
        searchService.index(List.of(doc("1", DUNE_TITLE)));
        searchService.search(DUNE_QUERY, 0, FULL_PAGE);

        searchService.remove("1");
        final BookSearchResult nextPage = searchService.search(DUNE_QUERY, FULL_PAGE, FULL_PAGE);

        assertThat(nextPage.hits()).isEmpty();
    }

    private static BookSearchDocument doc(final String id, final String title) {
        return new BookSearchDocument(id, title, null, List.of(), List.of(), "en", FIRST_YEAR, POPULARITY);
    }
}
