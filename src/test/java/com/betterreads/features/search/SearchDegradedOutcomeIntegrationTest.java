package com.betterreads.features.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.betterreads.testsupport.ContainerizedTest;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "meilisearch.host=http://localhost:1",
    "meilisearch.master-key=unused",
    "meilisearch.index-name=books-test"
})
class SearchDegradedOutcomeIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String SEARCH_PATH = "/api/v1/search/books";

    private static final String EVENTS_PATH = "/api/v1/search/books/events";

    private static final String QUERY = "dune";

    private static final int OVERSIZED_QUERY_LENGTH = 201;

    @Autowired
    private BookSearchService searchService;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Test
    @DisplayName("flags the empty result as degraded when Meilisearch is unreachable")
    void flagsDegradedOnOutage() {
        final SearchOutcome outcome = searchService.search("anything", 0, 20);

        assertThat(outcome.degraded()).isTrue();
        assertThat(outcome.result().totalHits()).isZero();
        assertThat(outcome.result().hits()).isEmpty();
    }

    @Test
    void shouldReturnNoHitWhenMeilisearchIsDown() {
        final Optional<BookSearchDocument> hit = searchService.hitFor(QUERY, "1");

        assertThat(hit).isEmpty();
    }

    @Test
    void shouldThrowWhenIndexingDuringOutage() {
        final List<BookSearchDocument> documents = List.of(BookSearchDocuments.eyeOfTheWorld());

        assertThatThrownBy(() -> searchService.index(documents)).isInstanceOf(SearchIndexException.class);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequests")
    void shouldRejectInvalidSearchParameters(
        final String row, final String path, final String query, final String offset, final String limit)
        throws Exception {
        final MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        mockMvc.perform(get(path).param("q", query).param("offset", offset).param("limit", limit))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    static Stream<Arguments> invalidRequests() {
        return Stream.of(
            Arguments.of("blank query", SEARCH_PATH, " ", "0", "20"),
            Arguments.of("oversized query", SEARCH_PATH, "a".repeat(OVERSIZED_QUERY_LENGTH), "0", "20"),
            Arguments.of("negative offset", SEARCH_PATH, QUERY, "-1", "20"),
            Arguments.of("zero limit", SEARCH_PATH, QUERY, "0", "0"),
            Arguments.of("limit over 100", SEARCH_PATH, QUERY, "0", "101"),
            Arguments.of("blank stream query", EVENTS_PATH, " ", "0", "20"),
            Arguments.of("oversized stream query", EVENTS_PATH, "a".repeat(OVERSIZED_QUERY_LENGTH), "0", "20"));
    }
}
