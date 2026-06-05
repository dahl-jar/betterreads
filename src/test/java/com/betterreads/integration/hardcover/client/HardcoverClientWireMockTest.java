package com.betterreads.integration.hardcover.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.betterreads.catalog.service.source.BookFieldSource;
import com.betterreads.integration.hardcover.HardcoverProperties;
import org.assertj.core.api.Assertions;
import com.betterreads.integration.hardcover.HardcoverWebClientConfig;
import com.betterreads.integration.hardcover.mapper.HardcoverMapper;
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
 * Exercises the Hardcover client request path against a stubbed GraphQL boundary, so hit selection,
 * the title-drift guard, and 401/5xx handling run in CI with no live API and no Docker.
 *
 * <p>The stub bodies are trimmed copies of the real typesense payload observed on 2026-05-31,
 * including the case that matters most: the top hit is a one-rating stub and the canonical work is
 * a later hit with thousands of reads.
 */
@SpringBootTest(
    classes = {
        HardcoverWebClientConfig.class,
        HardcoverClientImpl.class,
        HardcoverMapper.class
    },
    properties = "spring.main.web-application-type=none"
)
@EnableConfigurationProperties(HardcoverProperties.class)
class HardcoverClientWireMockTest {

    private static final int CONNECT_TIMEOUT_MS = 2000;

    private static final int READ_TIMEOUT_MS = 5000;

    private static final int HTTP_UNAUTHORIZED = 401;

    private static final int HTTP_SERVER_ERROR = 503;

    private static final String GRAPHQL_PATH = "/v1/graphql";

    private static final String HOBBIT_TITLE = "The Hobbit";

    private static final String HOBBIT_AUTHOR = "J.R.R. Tolkien";

    private static final int HOBBIT_CANONICAL_RATING_COUNT = 6394;

    /** Top hit is a one-rating stub; the real Hobbit is hit two, with thousands of reads. */
    private static final String HOBBIT_SEARCH_JSON = """
        {
          "data": {
            "search": {
              "results": {
                "hits": [
                  {"document": {
                    "id": "1", "title": "The Hobbit", "rating": 5.0, "ratings_count": 1,
                    "users_read_count": 1, "author_names": []
                  }},
                  {"document": {
                    "id": "9999", "title": "The Hobbit, or There and Back Again", "release_year": 1937,
                    "rating": 4.31, "ratings_count": 6394, "users_read_count": 8616,
                    "author_names": ["J.R.R. Tolkien"], "isbns": ["9788578276300"],
                    "genres": ["Fantasy", "Classics", "Fiction"],
                    "image": {"url": "https://assets.hardcover.app/hobbit.jpg"},
                    "featured_series": {"position": 0.0, "series": {"name": "The Lord of the Rings"}}
                  }}
                ]
              }
            }
          }
        }
        """;

    /** Title matches the query but the author does not; the author guard must reject it. */
    private static final String AUTHOR_DRIFT_SEARCH_JSON = """
        {
          "data": {"search": {"results": {"hits": [
            {"document": {
              "id": "42", "title": "The Hobbit", "rating": 4.8,
              "ratings_count": 5000, "users_read_count": 5000, "author_names": ["Some Other Author"]
            }}
          ]}}}
        }
        """;

    private static final String DRIFT_SEARCH_JSON = """
        {
          "data": {"search": {"results": {"hits": [
            {"document": {
              "title": "A Completely Different Book", "rating": 4.5,
              "ratings_count": 9000, "users_read_count": 9000, "author_names": ["Someone Else"]
            }}
          ]}}}
        }
        """;

    private static final WireMockServer WIREMOCK = startServer();

    @Autowired
    private HardcoverClientImpl client;

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

    @Nested
    @DisplayName("hit selection")
    class HitSelection {

        @Test
        @DisplayName("picks the highest-read hit, not rank 0, so the one-rating stub is skipped")
        void picksHighestReadHitNotRankZero() {
            WIREMOCK.stubFor(post(urlPathEqualTo(GRAPHQL_PATH)).willReturn(json(HOBBIT_SEARCH_JSON)));

            assertThat(client.fetchByTitleAuthor(HOBBIT_TITLE, HOBBIT_AUTHOR))
                .isPresent()
                .get()
                .satisfies(book -> {
                    assertThat(book.source()).isEqualTo(BookFieldSource.HARDCOVER);
                    assertThat(book.ratingCount())
                        .as("rank 0 is a 1-rating stub; selecting it would store rating 5.0 x 1")
                        .isEqualTo(HOBBIT_CANONICAL_RATING_COUNT);
                });
        }

        @Test
        @DisplayName("a picked hit whose title does not match the query is rejected as drift")
        void titleDriftRejected() {
            WIREMOCK.stubFor(post(urlPathEqualTo(GRAPHQL_PATH)).willReturn(json(DRIFT_SEARCH_JSON)));

            assertThat(client.fetchByTitleAuthor(HOBBIT_TITLE, HOBBIT_AUTHOR))
                .as("the highest-read hit is an unrelated book; matching on read count alone would "
                    + "persist the wrong work under the query title")
                .isEmpty();
        }

        @Test
        @DisplayName("a hit with the right title but a different author is rejected")
        void authorDriftRejected() {
            WIREMOCK.stubFor(post(urlPathEqualTo(GRAPHQL_PATH))
                .willReturn(json(AUTHOR_DRIFT_SEARCH_JSON)));

            assertThat(client.fetchByTitleAuthor(HOBBIT_TITLE, HOBBIT_AUTHOR))
                .as("Hardcover searches by title only, so a shared title under another author must "
                    + "not resolve for the requested author")
                .isEmpty();
        }
    }

    @Nested
    @DisplayName("error handling")
    class ErrorHandling {

        @Test
        @DisplayName("a 401 from an expired or revoked token resolves to empty, not an exception")
        void unauthorizedIsEmpty() {
            WIREMOCK.stubFor(post(urlPathEqualTo(GRAPHQL_PATH))
                .willReturn(aResponse().withStatus(HTTP_UNAUTHORIZED)));

            assertThat(client.fetchByTitleAuthor(HOBBIT_TITLE, HOBBIT_AUTHOR))
                .as("a rejected token resolves to empty, not a propagated exception")
                .isEmpty();
        }

        @Test
        @DisplayName("a 5xx propagates so a transient outage is not mistaken for 'book not found'")
        void serverErrorPropagates() {
            WIREMOCK.stubFor(post(urlPathEqualTo(GRAPHQL_PATH))
                .willReturn(aResponse().withStatus(HTTP_SERVER_ERROR)));

            Assertions.assertThatThrownBy(() -> client.fetchByTitleAuthor(HOBBIT_TITLE, HOBBIT_AUTHOR))
                .isInstanceOf(WebClientResponseException.class);
        }
    }
}
