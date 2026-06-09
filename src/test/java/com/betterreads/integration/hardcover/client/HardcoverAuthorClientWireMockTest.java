package com.betterreads.integration.hardcover.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.betterreads.catalog.service.source.SourceAuthorWorks;
import com.betterreads.catalog.service.source.SourceBook;
import com.betterreads.integration.hardcover.HardcoverProperties;
import com.betterreads.integration.hardcover.HardcoverWebClientConfig;
import com.betterreads.integration.hardcover.mapper.HardcoverAuthorMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Exercises the Hardcover author client against a stubbed GraphQL boundary. The stub returns the
 * Author search payload for the search call and the works payload for the enumeration call, matched
 * on the GraphQL query text.
 *
 * <p>The works payload puts each filter rule on its own row: a translation is a non-canonical
 * edition, a foreign edition is non-English, an anthology fails the single-book check, a bind-up
 * carries the compilation flag, and the remaining rows arrive in descending reader order.
 */
@SpringBootTest(
    classes = {
        HardcoverWebClientConfig.class,
        HardcoverAuthorClientImpl.class,
        HardcoverAuthorMapper.class
    },
    properties = "spring.main.web-application-type=none"
)
@EnableConfigurationProperties(HardcoverProperties.class)
class HardcoverAuthorClientWireMockTest {

    private static final int CONNECT_TIMEOUT_MS = 2000;

    private static final int READ_TIMEOUT_MS = 5000;

    private static final int HTTP_UNAUTHORIZED = 401;

    private static final int HTTP_SERVER_ERROR = 503;

    private static final String GRAPHQL_PATH = "/v1/graphql";

    private static final String SEARCH_MARKER = "query_type: \\\"Author\\\"";

    private static final String ENUM_MARKER = "contributions";

    private static final String QUERY = "Brandon Sanderson";

    private static final String MISTBORN = "Mistborn: The Final Empire";

    /** The real author has the most books; the collaboration entry with zero books outranks nothing. */
    private static final String SEARCH_JSON = """
        {"data": {"search": {"results": {"hits": [
          {"document": {"id": "999", "name": "Dan Wells, Brandon Sanderson", "books_count": 0}},
          {"document": {"id": "204214", "name": "Brandon Sanderson", "books_count": 198}}
        ]}}}}
        """;

    private static final String ENUM_JSON = """
        {"data": {"authors": [{
          "name": "Brandon Sanderson",
          "contributions": [
            {"book": {"id": 1, "title": "Mistborn: The Final Empire", "users_count": 9706,
              "canonical_id": null,
              "book_series": [
                {"position": 5, "featured": false, "series": {"name": "The Cosmere"}},
                {"position": 1, "featured": true, "series": {"name": "Mistborn"}}],
              "default_physical_edition": {"language": {"language": "English"}}}},
            {"book": {"id": 2, "title": "Ostatnie Imperium", "users_count": 50,
              "canonical_id": 1,
              "default_physical_edition": {"language": {"language": "Polish"}}}},
            {"book": {"id": 3, "title": "Die Seele des Königs", "users_count": 40,
              "canonical_id": null,
              "default_physical_edition": {"language": {"language": "German"}}}},
            {"book": {"id": 4, "title": "Dangerous Women (Boxed Set)", "users_count": 30,
              "canonical_id": null,
              "default_physical_edition": {"language": {"language": "English"}}}},
            {"book": {"id": 6, "title": "Mistborn & The Stormlight Archive", "users_count": 9000,
              "canonical_id": null, "book_category_id": 1, "compilation": true,
              "default_physical_edition": {"language": {"language": "English"}}}},
            {"book": {"id": 5, "title": "The Way of Kings", "users_count": 7897,
              "canonical_id": null,
              "default_physical_edition": {"language": {"language": "English"}}}},
            {"book": {"id": 7, "title": "The World of Mistborn", "users_count": 20,
              "canonical_id": null, "book_category_id": 1,
              "book_series": [
                {"position": null, "featured": true, "series": {"name": "Mistborn"}}],
              "default_physical_edition": {"language": {"language": "English"}}}}
          ]
        }]}}
        """;

    private static final String COMPANION = "The World of Mistborn";

    private static final WireMockServer WIREMOCK = startServer();

    @Autowired
    private HardcoverAuthorClientImpl client;

    private static WireMockServer startServer() {
        final WireMockServer server = new WireMockServer(0);
        server.start();
        return server;
    }

    private static ResponseDefinitionBuilder json(final String body) {
        return aResponse().withHeader("Content-Type", "application/json").withBody(body);
    }

    @AfterAll
    static void stopWireMock() {
        WIREMOCK.stop();
    }

    @BeforeEach
    void resetStubs() {
        WIREMOCK.resetAll();
    }

    @DynamicPropertySource
    static void hardcoverProperties(final DynamicPropertyRegistry registry) {
        registry.add("hardcover.base-url", () -> "http://localhost:" + WIREMOCK.port() + GRAPHQL_PATH);
        registry.add("hardcover.bearer-token", () -> "test-token");
        registry.add("hardcover.connect-timeout", () -> CONNECT_TIMEOUT_MS);
        registry.add("hardcover.read-timeout", () -> READ_TIMEOUT_MS);
    }

    private void stubSearchAndEnum() {
        WIREMOCK.stubFor(post(urlPathEqualTo(GRAPHQL_PATH))
            .withRequestBody(containing(SEARCH_MARKER)).willReturn(json(SEARCH_JSON)));
        WIREMOCK.stubFor(post(urlPathEqualTo(GRAPHQL_PATH))
            .withRequestBody(containing(ENUM_MARKER)).willReturn(json(ENUM_JSON)));
    }

    @Nested
    @DisplayName("candidate selection")
    class CandidateSelection {

        @Test
        @DisplayName("picks the author with the most books, not search rank 0")
        void picksMostBooksAuthorNotRankZero() {
            stubSearchAndEnum();

            final SourceAuthorWorks works = client.fetchAuthorWorks(QUERY).orElseThrow();

            assertThat(works.authorName()).isEqualTo(QUERY);
        }

        @Test
        @DisplayName("no search hit resolves to empty")
        void noHitIsEmpty() {
            WIREMOCK.stubFor(post(urlPathEqualTo(GRAPHQL_PATH)).withRequestBody(containing(SEARCH_MARKER))
                .willReturn(json("{\"data\": {\"search\": {\"results\": {\"hits\": []}}}}")));

            assertThat(client.fetchAuthorWorks(QUERY)).isEmpty();
        }
    }

    @Nested
    @DisplayName("works filtering")
    class WorksFiltering {

        @Test
        @DisplayName("keeps the English canonical single books in reader order, dropping the rest")
        void keepsEnglishCanonicalSingleBooksInReaderOrder() {
            stubSearchAndEnum();

            final SourceAuthorWorks works = client.fetchAuthorWorks(QUERY).orElseThrow();

            assertThat(works.books())
                .extracting(SourceBook::title)
                .containsExactly(MISTBORN, "The Way of Kings", COMPANION);
        }

        @Test
        @DisplayName("a compilation bind-up is dropped even with more readers than a real single book")
        void dropsCompilationBindUp() {
            stubSearchAndEnum();

            final SourceAuthorWorks works = client.fetchAuthorWorks(QUERY).orElseThrow();

            assertThat(works.books())
                .extracting(SourceBook::title)
                .as("the bind-up has 9000 readers, above The Way of Kings, so reader order alone "
                    + "would rank it second; the compilation flag is what excludes it")
                .doesNotContain("Mistborn & The Stormlight Archive");
        }

        @Test
        @DisplayName("a null-position featured membership leaves the book unlabelled, not volume null")
        void nullPositionMembershipCarriesNoSeries() {
            stubSearchAndEnum();

            final SourceAuthorWorks works = client.fetchAuthorWorks(QUERY).orElseThrow();

            assertThat(works.books())
                .filteredOn(book -> COMPANION.equals(book.title()))
                .first()
                .satisfies(book -> {
                    assertThat(book.seriesName()).isNull();
                    assertThat(book.seriesPosition()).isNull();
                });
        }

        @Test
        @DisplayName("a book carries its featured series name and position")
        void carriesFeaturedSeries() {
            stubSearchAndEnum();

            final SourceAuthorWorks works = client.fetchAuthorWorks(QUERY).orElseThrow();

            assertThat(works.books())
                .filteredOn(book -> MISTBORN.equals(book.title()))
                .first()
                .satisfies(book -> {
                    assertThat(book.seriesName())
                        .as("the featured membership wins over the Cosmere meta-series")
                        .isEqualTo("Mistborn");
                    assertThat(book.seriesPosition()).isEqualTo(1);
                });
        }
    }

    @Nested
    @DisplayName("error handling")
    class ErrorHandling {

        @Test
        @DisplayName("a 401 from a rejected token resolves to empty")
        void unauthorizedIsEmpty() {
            WIREMOCK.stubFor(post(urlPathEqualTo(GRAPHQL_PATH))
                .willReturn(aResponse().withStatus(HTTP_UNAUTHORIZED)));

            assertThat(client.fetchAuthorWorks(QUERY)).isEmpty();
        }

        @Test
        @DisplayName("a 5xx propagates so an outage is not read as 'author not found'")
        void serverErrorPropagates() {
            WIREMOCK.stubFor(post(urlPathEqualTo(GRAPHQL_PATH))
                .willReturn(aResponse().withStatus(HTTP_SERVER_ERROR)));

            assertThatThrownBy(() -> client.fetchAuthorWorks(QUERY))
                .isInstanceOf(WebClientResponseException.class);
        }
    }
}
