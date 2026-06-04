package com.betterreads.search.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.betterreads.search.dto.BookSearchDocument;
import com.betterreads.search.dto.BookSearchResult;
import com.betterreads.support.ContainerizedTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
 * Search against a real Meilisearch: an indexed book is found by title, typos still match, paging
 * slices the hits with the true total, and a removed book drops out of results.
 *
 * <p>The container master key is a deterministic test fixture, unrelated to any production
 * credential.
 */
@SpringBootTest
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
    "jwt.secret=integration-test-secret-must-be-at-least-256-bits-long-padding-padding",
    "jwt.issuer=betterreads-it"
})
class MeilisearchBookSearchServiceIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String TEST_MASTER_KEY = "testMasterKey1234567890";

    private static final int MEILISEARCH_PORT = 7700;

    private static final int FULL_PAGE = 20;

    private static final int PAGE_SIZE = 2;

    private static final int CORPUS_SIZE = 4;

    private static final int FIXTURE_YEAR = 1950;

    private static final double FIXTURE_POPULARITY = 9.0;

    private static final String TOLKIEN = "J.R.R. Tolkien";

    private static final String HOBBIT_TITLE = "The Hobbit";

    private static final String HOBBIT_QUERY = "hobbit";

    private static final String COMMON_QUERY = "the";

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
    static void overrideProps(final DynamicPropertyRegistry registry) {
        registry.add("meilisearch.host",
            () -> "http://" + MEILISEARCH.getHost() + ":" + MEILISEARCH.getMappedPort(MEILISEARCH_PORT));
        registry.add("meilisearch.master-key", () -> TEST_MASTER_KEY);
        registry.add("meilisearch.index-name", () -> "books-test");
    }

    @BeforeEach
    void indexCorpus() {
        cacheManager.getCache("searchResults").clear();
        searchService.index(List.of(
            doc("1", HOBBIT_TITLE, TOLKIEN),
            doc("2", "The Fellowship of the Ring", TOLKIEN),
            doc("3", "The Two Towers", TOLKIEN),
            doc("4", "The Return of the King", TOLKIEN)));
    }

    @Nested
    @DisplayName("search")
    class Search {

        @Test
        @DisplayName("finds a book by its title")
        void findsByTitle() {
            final BookSearchResult result = searchService.search(HOBBIT_QUERY, 0, FULL_PAGE);

            assertThat(result.hits())
                .extracting(BookSearchDocument::bookId)
                .containsExactly("1");
        }

        @Test
        @DisplayName("matches despite a typo in the query")
        void toleratesTypos() {
            final BookSearchResult result = searchService.search("hobbt", 0, FULL_PAGE);

            assertThat(result.hits())
                .extracting(BookSearchDocument::title)
                .containsExactly(HOBBIT_TITLE);
        }

        @Test
        @DisplayName("slices the hits by offset and limit with the full total")
        void pages() {
            final BookSearchResult firstPage = searchService.search(COMMON_QUERY, 0, PAGE_SIZE);
            final BookSearchResult secondPage = searchService.search(COMMON_QUERY, PAGE_SIZE, PAGE_SIZE);

            assertThat(firstPage.hits()).hasSize(PAGE_SIZE);
            assertThat(secondPage.hits()).hasSize(PAGE_SIZE);
            assertThat(firstPage.totalHits()).isEqualTo(CORPUS_SIZE);
            assertThat(secondPage.offset()).isEqualTo(PAGE_SIZE);
        }
    }

    @Nested
    @DisplayName("remove")
    class Remove {

        @Test
        @DisplayName("drops the removed book from later results")
        void removesFromIndex() {
            searchService.index(List.of(doc("99", "Silmarillion", TOLKIEN)));

            searchService.remove("99");

            final BookSearchResult result = searchService.search("silmarillion", 0, FULL_PAGE);
            assertThat(result.hits()).isEmpty();
        }
    }

    private static BookSearchDocument doc(final String id, final String title, final String author) {
        return new BookSearchDocument(
            id, title, null, List.of(author), List.of(), "en", FIXTURE_YEAR, FIXTURE_POPULARITY);
    }
}
