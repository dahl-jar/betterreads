package com.betterreads.integration.googlebooks.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import com.betterreads.catalog.service.BookFieldSource;
import com.betterreads.catalog.service.SourceBook;
import com.betterreads.integration.googlebooks.GoogleBooksProperties;
import com.betterreads.integration.googlebooks.GoogleBooksWebClientConfig;
import com.betterreads.integration.googlebooks.mapper.GoogleBooksMapper;
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
 * Exercises the Google Books client request path against a stubbed HTTP boundary, so search-result
 * parsing, the HTML-strip mapper rule, ISBN-13 selection, page count, and 4xx handling run in CI
 * with no live API and no key.
 *
 * <p>The stub bodies are trimmed copies of the real shapes the live client test documents: the
 * {@code volumeInfo} block, an {@code industryIdentifiers} array with both ISBN types, HTML in the
 * description, and {@code pageCount} present.
 */
@SpringBootTest(
    classes = {
        GoogleBooksWebClientConfig.class,
        GoogleBooksClientImpl.class,
        GoogleBooksMapper.class
    },
    properties = "spring.main.web-application-type=none"
)
@EnableConfigurationProperties(GoogleBooksProperties.class)
class GoogleBooksClientWireMockTest {

    private static final int CONNECT_TIMEOUT_MS = 2000;

    private static final int READ_TIMEOUT_MS = 5000;

    private static final int HTTP_NOT_FOUND = 404;

    private static final int DUNE_REISSUE_YEAR = 2016;

    private static final int DUNE_PAGE_COUNT = 412;

    private static final String SEARCH_PATH = "/volumes";

    private static final String VOLUME_PATH = "/volumes/TtkxEAAAQBAJ";

    private static final String VOLUME_ID = "TtkxEAAAQBAJ";

    private static final String DUNE_TITLE = "Dune";

    private static final WireMockServer WIREMOCK = startServer();

    private static final String SEARCH_JSON = """
        {
          "totalItems": 1,
          "items": [
            {
              "id": "TtkxEAAAQBAJ",
              "volumeInfo": {
                "title": "Dune",
                "authors": ["Frank Herbert"],
                "publishedDate": "2016-09-01",
                "publisher": "Penguin",
                "pageCount": 412,
                "language": "en",
                "industryIdentifiers": [
                  {"type": "ISBN_10", "identifier": "0143111582"},
                  {"type": "ISBN_13", "identifier": "9780143111580"}
                ],
                "categories": ["Fiction"],
                "description": "<p>Set on the desert planet <b>Arrakis</b>.</p>"
              }
            }
          ]
        }
        """;

    private static final String VOLUME_JSON = """
        {
          "id": "TtkxEAAAQBAJ",
          "volumeInfo": {
            "title": "Dune",
            "authors": ["Frank Herbert"],
            "publishedDate": "2016-09-01",
            "pageCount": 412,
            "language": "en"
          }
        }
        """;

    @Autowired
    private GoogleBooksClientImpl client;

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
    static void googleBooksProperties(final DynamicPropertyRegistry registry) {
        registry.add("googlebooks.base-url", () -> "http://localhost:" + WIREMOCK.port());
        registry.add("googlebooks.api-key", () -> "test-key");
        registry.add("googlebooks.connect-timeout", () -> CONNECT_TIMEOUT_MS);
        registry.add("googlebooks.read-timeout", () -> READ_TIMEOUT_MS);
    }

    @Nested
    @DisplayName("fetchByTitleAuthor")
    class FetchByTitleAuthor {

        @Test
        @DisplayName("parses the volume, strips HTML, picks ISBN-13, and keeps the page count")
        void parsesVolumeCleanly() {
            WIREMOCK.stubFor(get(urlPathEqualTo(SEARCH_PATH)).willReturn(json(SEARCH_JSON)));

            final Optional<SourceBook> result = client.fetchByTitleAuthor(DUNE_TITLE, "Frank Herbert");

            assertThat(result)
                .isPresent()
                .get()
                .satisfies(book -> {
                    assertThat(book.source()).isEqualTo(BookFieldSource.GOOGLE_BOOKS);
                    assertThat(book.googleBooksVolumeId()).isEqualTo(VOLUME_ID);
                    assertThat(book.isbn13()).isEqualTo("9780143111580");
                    assertThat(book.pageCount()).isEqualTo(DUNE_PAGE_COUNT);
                    assertThat(book.publicationYear()).isEqualTo(DUNE_REISSUE_YEAR);
                    assertThat(book.description())
                        .as("the mapper must strip the <p>/<b> tags Google ships")
                        .isEqualTo("Set on the desert planet Arrakis.");
                });
        }
    }

    @Nested
    @DisplayName("error handling")
    class ErrorHandling {

        @Test
        @DisplayName("a 404 resolves to empty, not an exception")
        void notFoundIsEmpty() {
            WIREMOCK.stubFor(get(urlPathEqualTo(SEARCH_PATH))
                .willReturn(aResponse().withStatus(HTTP_NOT_FOUND)));

            assertThat(client.fetchByTitleAuthor("Nope", "Nobody")).isEmpty();
        }

        @Test
        @DisplayName("a volume lookup by id maps the single-volume response")
        void fetchByVolumeId() {
            WIREMOCK.stubFor(get(urlPathEqualTo(VOLUME_PATH)).willReturn(json(VOLUME_JSON)));

            assertThat(client.fetchByVolumeId(VOLUME_ID))
                .isPresent()
                .get()
                .extracting(SourceBook::title)
                .isEqualTo(DUNE_TITLE);
        }
    }
}
