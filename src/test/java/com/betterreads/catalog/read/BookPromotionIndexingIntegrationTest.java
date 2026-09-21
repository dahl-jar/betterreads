package com.betterreads.catalog.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

import com.betterreads.catalog.repository.BookRepository;
import com.betterreads.catalog.repository.PendingBookRepository;
import com.betterreads.catalog.service.pipeline.PendingBookService;
import com.betterreads.catalog.service.source.model.SourceBooks;
import com.betterreads.catalog.service.source.merge.SourceMerger;
import com.betterreads.search.dto.BookSearchResult;
import com.betterreads.search.service.BookSearchService;
import com.betterreads.support.ContainerizedTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * A promoted book becomes searchable without waiting for the nightly reconcile, driven by the
 * after-commit index hook on promotion.
 */
@SpringBootTest
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
    "jwt.secret=integration-test-secret-must-be-at-least-256-bits-long-padding-padding",
    "jwt.issuer=betterreads-it",
    "betterreads.catalog.staging.poll-enabled=false"
})
@Import(NoNetworkSources.class)
class BookPromotionIndexingIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String TEST_MASTER_KEY = "testMasterKey1234567890";

    private static final int MEILISEARCH_PORT = 7700;

    private static final int FULL_PAGE = 20;

    private static final int INDEX_WAIT_SECONDS = 10;

    private static final int ONE_HIT = 1;

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
    private SourceMerger merger;

    @Autowired
    private PendingBookService pendingBookService;

    @Autowired
    private BookSearchService searchService;

    @Autowired
    private BookRepository books;

    @Autowired
    private PendingBookRepository pendingBooks;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @DynamicPropertySource
    static void meilisearchProps(final DynamicPropertyRegistry registry) {
        registry.add("meilisearch.host",
            () -> "http://" + MEILISEARCH.getHost() + ":" + MEILISEARCH.getMappedPort(MEILISEARCH_PORT));
        registry.add("meilisearch.master-key", () -> TEST_MASTER_KEY);
        registry.add("meilisearch.index-name", () -> "books-promote-test");
    }

    @BeforeEach
    void clearCatalog() {
        pendingBooks.deleteAll();
        books.deleteAll();
    }

    @Test
    void shouldPushPromotedBookToOpenSearchStream() throws Exception {
        final MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        final MvcResult stream = mockMvc.perform(get("/api/v1/search/books/events").param("q", DUNE_QUERY))
            .andExpect(request().asyncStarted())
            .andReturn();
        pendingBookService.stage(merger.merge(List.of(SourceBooks.dune())));

        pendingBookService.promoteReady();

        await().atMost(Duration.ofSeconds(INDEX_WAIT_SECONDS)).untilAsserted(() ->
            assertThat(stream.getResponse().getContentAsString())
                .contains("event:search-hit")
                .contains(SourceBooks.dune().dedupKey()));
    }

    @Test
    @DisplayName("a promoted book is searchable right after promotion")
    void promotedBookIsSearchable() {
        pendingBookService.stage(merger.merge(List.of(SourceBooks.dune())));

        pendingBookService.promoteReady();

        await().atMost(Duration.ofSeconds(INDEX_WAIT_SECONDS)).untilAsserted(() -> {
            final BookSearchResult result = searchService.search(DUNE_QUERY, 0, FULL_PAGE);
            assertThat(result.hits()).hasSize(ONE_HIT);
        });
    }
}
