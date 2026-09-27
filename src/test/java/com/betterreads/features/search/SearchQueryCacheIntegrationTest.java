package com.betterreads.features.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.betterreads.testsupport.ContainerizedTest;
import com.meilisearch.sdk.Client;
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
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** the book is removed after the first search, so a hit on the repeat can only come from the cache */
@SpringBootTest
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SearchQueryCacheIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final int FULL_PAGE = 20;

    private static final int FIRST_YEAR = 1965;

    private static final double POPULARITY = 9.0;

    private static final String DUNE_TITLE = "Dune";

    private static final String DUNE_QUERY = "dune";

    private static final String RED_RISING_ID = "2";

    private static final String RED_RISING_TITLE = "Red Rising";

    private static final String RED_RISING_QUERY = "red rising";

    private static final String INDEX_NAME = "books-querycache-test";

    @Autowired
    private BookSearchService searchService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private Client client;

    @DynamicPropertySource
    static void meilisearchProps(final DynamicPropertyRegistry registry) {
        MeilisearchServer.register(registry, INDEX_NAME);
    }

    @BeforeEach
    void clearCache() {
        cacheManager.getCache(SearchResultsCache.NAME).clear();
    }

    @Test
    @DisplayName("a repeated identical query reuses the first result")
    void cachesIdenticalQuery() {
        searchService.index(List.of(doc("1", DUNE_TITLE)));
        final BookSearchResult first = searchService.search(DUNE_QUERY, 0, FULL_PAGE).result();

        MeilisearchServer.removeFromIndex(client, INDEX_NAME, "1");
        final BookSearchResult repeat = searchService.search(DUNE_QUERY, 0, FULL_PAGE).result();

        assertThat(first.hits()).extracting(BookSearchDocument::bookId).containsExactly("1");
        assertThat(repeat.hits()).extracting(BookSearchDocument::bookId).containsExactly("1");
    }

    @Test
    @DisplayName("a different page is a separate cache entry")
    void differentPageMisses() {
        searchService.index(List.of(doc("1", DUNE_TITLE)));
        searchService.search(DUNE_QUERY, 0, FULL_PAGE);

        final BookSearchResult nextPage =
            searchService.search(DUNE_QUERY, FULL_PAGE, FULL_PAGE).result();

        assertThat(nextPage.hits()).isEmpty();
    }

    @Test
    void shouldNotCacheEmptyResult() {
        searchService.search(RED_RISING_QUERY, 0, FULL_PAGE);
        searchService.index(List.of(doc(RED_RISING_ID, RED_RISING_TITLE)));

        try {
            final BookSearchResult repeat = searchService.search(RED_RISING_QUERY, 0, FULL_PAGE).result();

            assertThat(repeat.hits()).extracting(BookSearchDocument::bookId).containsExactly(RED_RISING_ID);
        } finally {
            MeilisearchServer.removeFromIndex(client, INDEX_NAME, RED_RISING_ID);
        }
    }

    private static BookSearchDocument doc(final String id, final String title) {
        return BookSearchDocument.builder(id)
            .title(title)
            .language("en")
            .publicationYear(FIRST_YEAR)
            .popularityScore(POPULARITY)
            .build();
    }
}
