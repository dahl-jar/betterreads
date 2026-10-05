package com.betterreads.features.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.betterreads.testsupport.ContainerizedTest;
import com.betterreads.text.AuthorNames;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = "betterreads.catalog.staging.poll-enabled=false")
class MeilisearchAuthorSearchServiceIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final int PAGE = 20;

    private static final long KING_ID = 2L;

    private static final long TOLKIEN_ID = 3L;

    private static final long GEORGE_MARTIN_ID = 4L;

    private static final long GREENBERG_ID = 5L;

    private static final long BRANDON_SANDERSON_ID = 6L;

    private static final long CREDIT_LIST_ID = 7L;

    private static final long CHRISTOPHER_TOLKIEN_ID = 8L;

    private static final long STEVE_MARTIN_ID = 9L;

    private static final long MARTIN_AMIS_ID = 10L;

    private static final double FAMILIAR = 40.0;

    private static final double NICHE = 0.4;

    private static final double POPULAR = 61.0;

    private static final String TOLKIEN_ALIAS = "John Ronald Reuel Tolkien";

    @Autowired
    private AuthorSearchService searchService;

    @Autowired
    @Qualifier("searchCacheManager")
    private CacheManager searchCacheManager;

    @DynamicPropertySource
    static void meilisearch(final DynamicPropertyRegistry registry) {
        MeilisearchServer.register(registry, "books-author-search-test");
    }

    @BeforeEach
    void setUp() {
        searchCacheManager.getCache(SearchResultsCache.AUTHORS_NAME).clear();
        searchService.index(List.of(
            author(1L, "Ellen King", List.of(), NICHE),
            author(KING_ID, "Stephen King", List.of("STEPHEN KING"), POPULAR),
            author(TOLKIEN_ID, "J.R.R. Tolkien", List.of(TOLKIEN_ALIAS), POPULAR),
            author(CHRISTOPHER_TOLKIEN_ID, "Christopher Tolkien", List.of(), FAMILIAR),
            author(GEORGE_MARTIN_ID, "George R. R. Martin", List.of(), POPULAR),
            author(GREENBERG_ID, "Martin H. Greenberg", List.of(), FAMILIAR),
            author(BRANDON_SANDERSON_ID, "Brandon Sanderson", List.of(), POPULAR),
            author(CREDIT_LIST_ID, "Sanderson, Peter, DeFalco, Tom", List.of(), NICHE),
            author(STEVE_MARTIN_ID, "Steve Martin", List.of(), NICHE),
            author(MARTIN_AMIS_ID, "Martin Amis", List.of(), POPULAR)));
    }

    @Test
    void shouldRankPopularAuthorFirst() {
        final AuthorSearchResult result = searchService.search("king", 0, PAGE);

        assertThat(result.hits()).extracting(AuthorSearchDocument::authorId).startsWith(KING_ID);
    }

    @ParameterizedTest
    @CsvSource({"tolkien, 3", "martin, 4", "sanderson, 6"})
    void shouldRankSurnameMatchFirst(final String query, final long expectedFirst) {
        final AuthorSearchResult result = searchService.search(query, 0, PAGE);

        assertThat(result.hits()).extracting(AuthorSearchDocument::authorId).startsWith(expectedFirst);
    }

    @Test
    void shouldRankLessPopularSurnameAboveFirstName() {
        final AuthorSearchResult result = searchService.search("martin", 0, PAGE);

        final List<Long> ids = result.hits().stream().map(AuthorSearchDocument::authorId).toList();
        assertThat(ids.indexOf(STEVE_MARTIN_ID)).isLessThan(ids.indexOf(MARTIN_AMIS_ID));
    }

    @Test
    void shouldMatchMergedAlias() {
        final AuthorSearchResult result = searchService.search("ronald reuel", 0, PAGE);

        assertThat(result.hits()).extracting(AuthorSearchDocument::authorId).containsExactly(TOLKIEN_ID);
    }

    private static AuthorSearchDocument author(
        final long authorId, final String name, final List<String> aliases, final double popularity) {
        return new AuthorSearchDocument(
            authorId, name, AuthorNames.surname(name), aliases, null, 1, popularity, List.of());
    }
}
