package com.betterreads.integration.openlibrary.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import com.betterreads.catalog.service.source.BookFieldSource;
import com.betterreads.catalog.service.source.SourceBook;
import com.betterreads.integration.openlibrary.OpenLibraryProperties;
import com.betterreads.integration.openlibrary.OpenLibraryWebClientConfig;
import com.betterreads.integration.openlibrary.mapper.OpenLibraryMapper;
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

/**
 * Exercises the full OpenLibrary client request path against a stubbed HTTP boundary, so the
 * search-then-work-detail flow, DTO deserialization, title-drift guard, and 4xx handling run in
 * CI with no live API and no Docker.
 *
 * <p>The stub bodies are trimmed copies of the real shapes observed on the live API: the bulk
 * {@code isbn} array, the {@code {type, value}} description object, the {@code cover_i} field, and
 * a search result whose title differs from the query (the drift case).
 */
@SpringBootTest(
    classes = {
        OpenLibraryWebClientConfig.class,
        OpenLibraryClientImpl.class,
        OpenLibraryMapper.class
    },
    properties = "spring.main.web-application-type=none"
)
@EnableConfigurationProperties(OpenLibraryProperties.class)
class OpenLibraryClientWireMockTest {

    private static final int CONNECT_TIMEOUT_MS = 2000;

    private static final int READ_TIMEOUT_MS = 5000;

    private static final int HTTP_NOT_FOUND = 404;

    private static final int HOBBIT_FIRST_PUBLISHED = 1937;

    private static final String SEARCH_PATH = "/search.json";

    private static final String HOBBIT_WORK_PATH = "/works/OL27482W.json";

    private static final String HOBBIT_WORK_KEY = "OL27482W";

    private static final String HOBBIT_TITLE = "The Hobbit";

    private static final String HOBBIT_AUTHOR = "Tolkien";

    private static final String MISSING_TITLE = "Nope";

    private static final String MISSING_AUTHOR = "Nobody";

    private static final String GENRE_FANTASY = "fantasy";

    private static final String GENRE_CLASSICS = "classics";

    private static final WireMockServer WIREMOCK = startServer();

    private static final String HOBBIT_SEARCH_JSON = """
        {
          "numFound": 1,
          "docs": [
            {
              "key": "/works/OL27482W",
              "title": "The Hobbit",
              "author_name": ["J.R.R. Tolkien"],
              "first_publish_year": 1937,
              "cover_i": 14627509,
              "isbn": ["9780395282656", "0261103342"],
              "language": ["eng", "ger"]
            }
          ]
        }
        """;

    private static final String HOBBIT_WORK_JSON = """
        {
          "key": "/works/OL27482W",
          "title": "The Hobbit",
          "description": {"type": "/type/text", "value": "A tale of high adventure."},
          "subjects": ["Fantasy", "thrushes", "Fantasy fiction", "the one ring", "Classics"]
        }
        """;

    private static final String EMPTY_SEARCH_JSON = "{\"numFound\": 0, \"docs\": []}";

    private static final String DRIFT_SEARCH_JSON = """
        {
          "numFound": 1,
          "docs": [
            {"key": "/works/OL999W", "title": "Some Unrelated Sequel", "first_publish_year": 2010}
          ]
        }
        """;

    @Autowired
    private OpenLibraryClientImpl client;

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
    static void openLibraryProperties(final DynamicPropertyRegistry registry) {
        registry.add("openlibrary.base-url", () -> "http://localhost:" + WIREMOCK.port());
        registry.add("openlibrary.contact-email", () -> "test@betterreadsapp.com");
        registry.add("openlibrary.connect-timeout", () -> CONNECT_TIMEOUT_MS);
        registry.add("openlibrary.read-timeout", () -> READ_TIMEOUT_MS);
    }

    @Nested
    @DisplayName("fetchByTitleAuthor: search then work detail")
    class FetchByTitleAuthor {

        @Test
        @DisplayName("maps the full work into a SourceBook with clean genres and original year")
        void mapsFullWork() {
            WIREMOCK.stubFor(get(urlPathEqualTo(SEARCH_PATH)).willReturn(json(HOBBIT_SEARCH_JSON)));
            WIREMOCK.stubFor(get(urlPathEqualTo(HOBBIT_WORK_PATH)).willReturn(json(HOBBIT_WORK_JSON)));

            final Optional<SourceBook> result = client.fetchByTitleAuthor(HOBBIT_TITLE, HOBBIT_AUTHOR);

            assertThat(result)
                .isPresent()
                .get()
                .satisfies(book -> {
                    assertThat(book.source()).isEqualTo(BookFieldSource.OPEN_LIBRARY);
                    assertThat(book.openLibraryWorkKey()).isEqualTo(HOBBIT_WORK_KEY);
                    assertThat(book.publicationYear()).isEqualTo(HOBBIT_FIRST_PUBLISHED);
                    assertThat(book.coverUrl())
                        .isEqualTo("https://covers.openlibrary.org/b/id/14627509-L.jpg");
                    assertThat(book.description()).isEqualTo("A tale of high adventure.");
                    assertThat(book.rawSubjects())
                        .as("the {type,value} description and canonical-genre reduction run "
                            + "end-to-end through the real client, not just the mapper unit test")
                        .contains(GENRE_FANTASY, "fiction", GENRE_CLASSICS)
                        .doesNotContain("thrushes", "the one ring", "fantasy fiction");
                });
        }

        @Test
        @DisplayName("a search result whose title does not match the query is rejected as drift")
        void titleDriftRejected() {
            WIREMOCK.stubFor(get(urlPathEqualTo(SEARCH_PATH)).willReturn(json(DRIFT_SEARCH_JSON)));

            final Optional<SourceBook> result = client.fetchByTitleAuthor(HOBBIT_TITLE, HOBBIT_AUTHOR);

            assertThat(result)
                .as("the returned title 'Some Unrelated Sequel' does not contain the query, so the "
                    + "client must not return the drift match")
                .isEmpty();
        }

        @Test
        @DisplayName("a prefix-drift result (The Sandman -> The Sandman - Overture) is rejected")
        void prefixDriftRejected() {
            final String overtureJson = """
                {"numFound": 1, "docs": [
                  {"key": "/works/OL21213336W", "title": "The Sandman - Overture",
                   "first_publish_year": 2015}
                ]}
                """;
            WIREMOCK.stubFor(get(urlPathEqualTo(SEARCH_PATH)).willReturn(json(overtureJson)));

            final Optional<SourceBook> result = client.fetchByTitleAuthor("The Sandman", "Neil Gaiman");

            assertThat(result)
                .as("'The Sandman - Overture' extends the query with extra title content, so it is a "
                    + "different work; the seed proved this drift was being persisted as 'The Sandman'")
                .isEmpty();
        }
    }

    @Nested
    @DisplayName("search: multi-result discovery list")
    class Search {

        private static final int SEARCH_LIMIT = 10;

        private static final int SERIES_HIT_COUNT = 3;

        private static final String SERIES_SEARCH_JSON = """
            {
              "numFound": 3,
              "docs": [
                {"key": "/works/OL1W", "title": "The Eye of the World",
                 "author_name": ["Robert Jordan"], "first_publish_year": 1990},
                {"key": "/works/OL2W", "title": "The Great Hunt",
                 "author_name": ["Robert Jordan"], "first_publish_year": 1990},
                {"key": "/works/OL3W", "title": "The Dragon Reborn",
                 "author_name": ["Robert Jordan"], "first_publish_year": 1991}
              ]
            }
            """;

        @Test
        @DisplayName("a multi-hit search returns one SourceBook per hit, each with its work key")
        void mapsEveryHit() {
            WIREMOCK.stubFor(get(urlPathEqualTo(SEARCH_PATH)).willReturn(json(SERIES_SEARCH_JSON)));

            final List<SourceBook> results = client.search("The Wheel of Time", SEARCH_LIMIT);

            assertThat(results)
                .as("each series volume is a distinct book, not collapsed to one")
                .hasSize(SERIES_HIT_COUNT)
                .extracting(SourceBook::openLibraryWorkKey)
                .containsExactly("OL1W", "OL2W", "OL3W");
        }

        @Test
        @DisplayName("an empty docs array returns an empty list, not null")
        void emptyResultIsEmptyList() {
            WIREMOCK.stubFor(get(urlPathEqualTo(SEARCH_PATH))
                .willReturn(json(EMPTY_SEARCH_JSON)));

            assertThat(client.search("nothing matches this", SEARCH_LIMIT)).isEmpty();
        }

        @Test
        @DisplayName("a 404 from search resolves to an empty list, not an exception")
        void notFoundIsEmptyList() {
            WIREMOCK.stubFor(get(urlPathEqualTo(SEARCH_PATH))
                .willReturn(aResponse().withStatus(HTTP_NOT_FOUND)));

            assertThat(client.search("anything", SEARCH_LIMIT)).isEmpty();
        }
    }

    @Nested
    @DisplayName("error handling")
    class ErrorHandling {

        @Test
        @DisplayName("a 404 from search resolves to empty, not an exception")
        void notFoundIsEmpty() {
            WIREMOCK.stubFor(get(urlPathEqualTo(SEARCH_PATH))
                .willReturn(aResponse().withStatus(HTTP_NOT_FOUND)));

            assertThat(client.fetchByTitleAuthor(MISSING_TITLE, MISSING_AUTHOR)).isEmpty();
        }

        @Test
        @DisplayName("an empty docs array resolves to empty")
        void noDocsIsEmpty() {
            WIREMOCK.stubFor(get(urlPathEqualTo(SEARCH_PATH))
                .willReturn(json(EMPTY_SEARCH_JSON)));

            assertThat(client.fetchByTitleAuthor(MISSING_TITLE, MISSING_AUTHOR)).isEmpty();
        }
    }

    @Nested
    @DisplayName("fetchByWorkKey")
    class FetchByWorkKey {

        @Test
        @DisplayName("fetches the work directly and maps its subjects")
        void fetchesWorkDirectly() {
            WIREMOCK.stubFor(get(urlPathEqualTo(HOBBIT_WORK_PATH)).willReturn(json(HOBBIT_WORK_JSON)));

            assertThat(client.fetchByWorkKey(HOBBIT_WORK_KEY))
                .isPresent()
                .get()
                .satisfies(book -> assertThat(book.rawSubjects()).contains(GENRE_FANTASY, GENRE_CLASSICS));
        }
    }
}
