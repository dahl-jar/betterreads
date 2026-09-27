package com.betterreads.features.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

import com.betterreads.book.BookRepository;
import com.betterreads.bookmerge.SourceMerger;
import com.betterreads.booksource.SourceBooks;
import com.betterreads.features.bookstaging.PendingBookService;
import com.betterreads.pendingbook.PendingBookRepository;
import com.betterreads.testsupport.ContainerizedTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
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
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
    "betterreads.catalog.staging.poll-enabled=false"
})
@Import(NoNetworkSources.class)
class BookPromotionIndexingIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final int INDEX_WAIT_SECONDS = 10;

    private static final String DUNE_QUERY = "dune";

    @Autowired
    private SourceMerger merger;

    @Autowired
    private PendingBookService pendingBookService;

    @Autowired
    private BookRepository books;

    @Autowired
    private PendingBookRepository pendingBooks;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @DynamicPropertySource
    static void meilisearchProps(final DynamicPropertyRegistry registry) {
        MeilisearchServer.register(registry, "books-promote-test");
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
        pendingBookService.stage(merger.merge(null, List.of(SourceBooks.dune())));

        pendingBookService.promoteReady();

        await().atMost(Duration.ofSeconds(INDEX_WAIT_SECONDS)).untilAsserted(() ->
            assertThat(stream.getResponse().getContentAsString())
                .contains("event:search-hit")
                .contains(SourceBooks.dune().dedupKey()));
    }
}
