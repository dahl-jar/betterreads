package com.betterreads.clients.googlebooks;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.SourceBook;
import com.betterreads.clients.WireMockFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@SpringBootTest(
    classes = {
        GoogleBooksWebClientConfig.class,
        GoogleBooksClientImpl.class,
        GoogleBooksMapper.class
    },
    properties = "spring.main.web-application-type=none"
)
@EnableConfigurationProperties(GoogleBooksProperties.class)
class GoogleBooksClientWireMockTest extends WireMockFixture {

    private static final int DUNE_REISSUE_YEAR = 2016;

    private static final int DUNE_PAGE_COUNT = 412;

    private static final String SEARCH_PATH = "/volumes";

    private static final String VOLUME_ID = "TtkxEAAAQBAJ";

    private static final String DUNE_TITLE = "Dune";

    private static final String DUNE_AUTHOR = "Frank Herbert";

    private static final String DUNE_ISBN = "9780143111580";

    private static final String API_KEY = "test-key";

    private static final String SEARCH_BODY = """
        {
          "items": [
            {
              "id": "TtkxEAAAQBAJ",
              "volumeInfo": {
                "title": "Dune",
                "publishedDate": "2016-09-01",
                "pageCount": 412,
                "industryIdentifiers": [
                  {"type": "ISBN_10", "identifier": "0143111582"},
                  {"type": "ISBN_13", "identifier": "9780143111580"}
                ],
                "description": "<p>Set on the desert planet <b>Arrakis</b>.</p>"
              }
            }
          ]
        }
        """;

    @Autowired
    private GoogleBooksClientImpl client;

    @DynamicPropertySource
    static void googleBooksProperties(final DynamicPropertyRegistry registry) {
        registerSource(registry, "googlebooks", "");
        registry.add("googlebooks.api-key", () -> API_KEY);
    }

    @Nested
    @DisplayName("fetchByTitleAuthor")
    class FetchByTitleAuthor {

        @Test
        @DisplayName("maps the first search hit")
        void parsesVolumeCleanly() {
            WIREMOCK.stubFor(get(urlPathEqualTo(SEARCH_PATH))
                .willReturn(okJson(SEARCH_BODY)));

            final Optional<SourceBook> result = client.fetchByTitleAuthor(DUNE_TITLE, DUNE_AUTHOR);

            assertThat(result)
                .isPresent()
                .get()
                .satisfies(book -> {
                    assertThat(book.source()).isEqualTo(BookFieldSource.GOOGLE_BOOKS);
                    assertThat(book.googleBooksVolumeId()).isEqualTo(VOLUME_ID);
                    assertThat(book.isbn13()).isEqualTo(DUNE_ISBN);
                    assertThat(book.pageCount()).isEqualTo(DUNE_PAGE_COUNT);
                    assertThat(book.publicationYear()).isEqualTo(DUNE_REISSUE_YEAR);
                    assertThat(book.description())
                        .as("should strip the <p> and <b> tags from Google's description")
                        .isEqualTo("Set on the desert planet Arrakis.");
                });
        }

        @Test
        void shouldStripQuoteCharactersFromTheQuery() {
            WIREMOCK.stubFor(get(urlPathEqualTo(SEARCH_PATH)).willReturn(okJson(SEARCH_BODY)));

            client.fetchByTitleAuthor("Du\"ne", "Frank \\Herbert");

            WIREMOCK.verify(getRequestedFor(urlPathEqualTo(SEARCH_PATH))
                .withQueryParam("q", equalTo("intitle:\"Dune\" inauthor:\"Frank Herbert\"")));
        }

        @Test
        void shouldReturnEmptyWhenTheSearchHasNoHits() {
            WIREMOCK.stubFor(get(urlPathEqualTo(SEARCH_PATH)).willReturn(okJson("{\"totalItems\": 0}")));

            final Optional<SourceBook> result = client.fetchByTitleAuthor(DUNE_TITLE, DUNE_AUTHOR);

            assertThat(result).isEmpty();
        }

        @Test
        void shouldSendTheApiKeyAsAQueryParam() {
            WIREMOCK.stubFor(get(urlPathEqualTo(SEARCH_PATH)).willReturn(okJson(SEARCH_BODY)));

            client.fetchByTitleAuthor(DUNE_TITLE, DUNE_AUTHOR);

            WIREMOCK.verify(getRequestedFor(urlPathEqualTo(SEARCH_PATH))
                .withQueryParam("key", equalTo(API_KEY)));
        }
    }

    @Nested
    @DisplayName("fetchByIsbn")
    class FetchByIsbn {

        @Test
        void shouldSearchByIsbn() {
            WIREMOCK.stubFor(get(urlPathEqualTo(SEARCH_PATH)).willReturn(okJson(SEARCH_BODY)));

            final Optional<SourceBook> result = client.fetchByIsbn(DUNE_ISBN);

            WIREMOCK.verify(getRequestedFor(urlPathEqualTo(SEARCH_PATH))
                .withQueryParam("q", equalTo("isbn:" + DUNE_ISBN)));
            assertThat(result).map(SourceBook::isbn13).contains(DUNE_ISBN);
        }
    }

    @Nested
    @DisplayName("error handling")
    class ErrorHandling {

        @Test
        @DisplayName("a 404 on the search resolves to empty")
        void notFoundIsEmpty() {
            WIREMOCK.stubFor(get(urlPathEqualTo(SEARCH_PATH))
                .willReturn(aResponse().withStatus(HTTP_NOT_FOUND)));

            final Optional<SourceBook> result = client.fetchByTitleAuthor("Nope", "Nobody");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should throw when search returns 503")
        void shouldThrowOnServerError() {
            WIREMOCK.stubFor(get(urlPathEqualTo(SEARCH_PATH))
                .willReturn(aResponse().withStatus(HTTP_SERVER_ERROR)));

            assertThatThrownBy(() -> client.fetchByTitleAuthor(DUNE_TITLE, DUNE_AUTHOR))
                .isInstanceOf(WebClientResponseException.class);
        }
    }
}
