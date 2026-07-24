package com.betterreads.integration.openlibrary.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import com.betterreads.catalog.service.source.model.BookFieldSource;
import com.betterreads.catalog.service.source.model.SourceBook;
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

    private static final int NINETEEN_EIGHTY_FOUR_FIRST_PUBLISHED = 1949;

    private static final String SEARCH_PATH = "/search.json";

    private static final String HOBBIT_WORK_PATH = "/works/OL27482W.json";

    private static final String HOBBIT_WORK_KEY = "OL27482W";

    private static final String HOBBIT_TITLE = "The Hobbit";

    private static final String HOBBIT_AUTHOR = "Tolkien";

    private static final String MISSING_TITLE = "Nope";

    private static final String MISSING_AUTHOR = "Nobody";

    private static final String GENRE_FANTASY = "fantasy";

    private static final String GENRE_FICTION = "fiction";

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
                        .containsExactlyInAnyOrder(GENRE_FANTASY, GENRE_FICTION, GENRE_CLASSICS);
                });
        }

        @Test
        void picksCanonicalEarliestYear() {
            final String multiHitJson = """
                {"numFound": 4, "docs": [
                  {"key": "/works/OL_ADAPT", "title": "1984 (adaptation)", "first_publish_year": 2003},
                  {"key": "/works/OL_REPRINT", "title": "1984", "first_publish_year": 2021},
                  {"key": "/works/OL_CANON", "title": "1984", "first_publish_year": 1949},
                  {"key": "/works/OL_OTHER", "title": "1984", "first_publish_year": 1984}
                ]}
                """;
            WIREMOCK.stubFor(get(urlPathEqualTo(SEARCH_PATH)).willReturn(json(multiHitJson)));
            WIREMOCK.stubFor(get(urlPathEqualTo("/works/OL_CANON.json"))
                .willReturn(json("{\"key\": \"/works/OL_CANON\", \"description\": \"A dystopia.\"}")));

            final Optional<SourceBook> result = client.fetchByTitleAuthor("1984", "George Orwell");

            assertThat(result)
                .isPresent()
                .get()
                .satisfies(book -> {
                    assertThat(book.openLibraryWorkKey()).isEqualTo("OL_CANON");
                    assertThat(book.publicationYear()).isEqualTo(NINETEEN_EIGHTY_FOUR_FIRST_PUBLISHED);
                });
        }

        @Test
        void prefixDriftRejected() {
            final String overtureJson = """
                {"numFound": 1, "docs": [
                  {"key": "/works/OL21213336W", "title": "The Sandman - Overture",
                   "first_publish_year": 2015}
                ]}
                """;
            WIREMOCK.stubFor(get(urlPathEqualTo(SEARCH_PATH)).willReturn(json(overtureJson)));

            final Optional<SourceBook> result = client.fetchByTitleAuthor("The Sandman", "Neil Gaiman");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("fetchByIsbn")
    class FetchByIsbn {

        @Test
        void mapsFirstHit() {
            WIREMOCK.stubFor(get(urlPathEqualTo(SEARCH_PATH)).willReturn(json(HOBBIT_SEARCH_JSON)));
            WIREMOCK.stubFor(get(urlPathEqualTo(HOBBIT_WORK_PATH)).willReturn(json(HOBBIT_WORK_JSON)));

            final Optional<SourceBook> result = client.fetchByIsbn("9780395282656");

            assertThat(result)
                .isPresent()
                .get()
                .satisfies(book -> {
                    assertThat(book.openLibraryWorkKey()).isEqualTo(HOBBIT_WORK_KEY);
                    assertThat(book.title()).isEqualTo(HOBBIT_TITLE);
                });
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
        void mapsEveryHit() {
            WIREMOCK.stubFor(get(urlPathEqualTo(SEARCH_PATH)).willReturn(json(SERIES_SEARCH_JSON)));

            final List<SourceBook> results = client.search("The Wheel of Time", SEARCH_LIMIT);

            assertThat(results)
                .hasSize(SERIES_HIT_COUNT)
                .extracting(SourceBook::openLibraryWorkKey)
                .containsExactly("OL1W", "OL2W", "OL3W");
        }

        @Test
        void emptyResultIsEmptyList() {
            WIREMOCK.stubFor(get(urlPathEqualTo(SEARCH_PATH))
                .willReturn(json(EMPTY_SEARCH_JSON)));

            final List<SourceBook> results = client.search("nothing matches this", SEARCH_LIMIT);

            assertThat(results).isEmpty();
        }

        @Test
        void notFoundIsEmptyList() {
            WIREMOCK.stubFor(get(urlPathEqualTo(SEARCH_PATH))
                .willReturn(aResponse().withStatus(HTTP_NOT_FOUND)));

            final List<SourceBook> results = client.search("anything", SEARCH_LIMIT);

            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("error handling")
    class ErrorHandling {

        @Test
        void notFoundIsEmpty() {
            WIREMOCK.stubFor(get(urlPathEqualTo(SEARCH_PATH))
                .willReturn(aResponse().withStatus(HTTP_NOT_FOUND)));

            final Optional<SourceBook> result = client.fetchByTitleAuthor(MISSING_TITLE, MISSING_AUTHOR);

            assertThat(result).isEmpty();
        }

        @Test
        void noDocsIsEmpty() {
            WIREMOCK.stubFor(get(urlPathEqualTo(SEARCH_PATH))
                .willReturn(json(EMPTY_SEARCH_JSON)));

            final Optional<SourceBook> result = client.fetchByTitleAuthor(MISSING_TITLE, MISSING_AUTHOR);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("fetchByWorkKey")
    class FetchByWorkKey {

        @Test
        void fetchesWorkDirectly() {
            WIREMOCK.stubFor(get(urlPathEqualTo(HOBBIT_WORK_PATH)).willReturn(json(HOBBIT_WORK_JSON)));

            final Optional<SourceBook> result = client.fetchByWorkKey(HOBBIT_WORK_KEY);

            assertThat(result)
                .isPresent()
                .get()
                .satisfies(book -> assertThat(book.rawSubjects())
                    .containsExactlyInAnyOrder(GENRE_FANTASY, GENRE_FICTION, GENRE_CLASSICS));
        }
    }
}
