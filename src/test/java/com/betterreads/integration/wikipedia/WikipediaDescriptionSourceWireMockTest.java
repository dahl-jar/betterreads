package com.betterreads.integration.wikipedia;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import com.betterreads.catalog.service.source.BookFieldSource;
import com.betterreads.catalog.service.source.DescriptionLookup;
import com.betterreads.integration.wikidata.WikidataApi;
import com.betterreads.integration.wikidata.WikidataProperties;
import com.betterreads.integration.wikidata.WikidataWebClientConfig;
import com.github.tomakehurst.wiremock.WireMockServer;
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
 * Exercises the Wikipedia description source against a stubbed HTTP boundary: the Wikidata entity
 * carries the {@code enwiki} sitelink, the Wikipedia REST summary carries the extract. A
 * disambiguation summary, a missing sitelink, and a 404 each resolve to empty so the merge falls
 * through to the next source.
 *
 * <p>One WireMock server serves both hosts; the Wikidata and Wikipedia clients point at it.
 */
@SpringBootTest(
    classes = {
        WikidataWebClientConfig.class,
        WikipediaWebClientConfig.class,
        WikidataApi.class,
        WikipediaApi.class,
        WikipediaDescriptionSource.class
    },
    properties = "spring.main.web-application-type=none"
)
@EnableConfigurationProperties({WikidataProperties.class, WikipediaProperties.class})
class WikipediaDescriptionSourceWireMockTest {

    private static final int CONNECT_TIMEOUT_MS = 2000;

    private static final int READ_TIMEOUT_MS = 5000;

    private static final int HTTP_OK = 200;

    private static final int HTTP_NOT_FOUND = 404;

    private static final String QID = "Q190192";

    private static final String ISBN = "9780441013593";

    private static final String TITLE = "Dune";

    private static final String AUTHOR = "Frank Herbert";

    private static final String JSON_CONTENT_TYPE = "application/json";

    private static final String CONTENT_TYPE_HEADER = "Content-Type";

    private static final String ENTITY_PATH = "/wiki/Special:EntityData/Q190192.json";

    private static final String SUMMARY_PATH = "/api/rest_v1/page/summary/Dune_(novel)";

    private static final String EXTRACT =
        "Dune is a 1965 epic science fiction novel by American author Frank Herbert, the first in a "
        + "long series set on the desert planet Arrakis.";

    private static final String ENTITY_JSON = """
        { "entities": { "Q190192": { "sitelinks": {
            "enwiki": { "site": "enwiki", "title": "Dune (novel)" } } } } }
        """;

    private static final WireMockServer WIREMOCK = startServer();

    @Autowired
    private WikipediaDescriptionSource source;

    @DynamicPropertySource
    static void wireProperties(final DynamicPropertyRegistry registry) {
        final String baseUrl = "http://localhost:" + WIREMOCK.port();
        registry.add("wikidata.base-url", () -> baseUrl);
        registry.add("wikidata.connect-timeout", () -> CONNECT_TIMEOUT_MS);
        registry.add("wikidata.read-timeout", () -> READ_TIMEOUT_MS);
        registry.add("wikipedia.base-url", () -> baseUrl);
        registry.add("wikipedia.connect-timeout", () -> CONNECT_TIMEOUT_MS);
        registry.add("wikipedia.read-timeout", () -> READ_TIMEOUT_MS);
    }

    @BeforeEach
    void resetStubs() {
        WIREMOCK.resetAll();
    }

    @AfterAll
    static void stopServer() {
        WIREMOCK.stop();
    }

    @Nested
    @DisplayName("fetch")
    class Fetch {

        @Test
        @DisplayName("source identity is Wikipedia")
        void sourceIdentity() {
            assertThat(source.source()).isEqualTo(BookFieldSource.WIKIPEDIA);
        }

        @Test
        @DisplayName("resolves the enwiki sitelink and returns the summary extract")
        void returnsExtract() {
            stubJson(ENTITY_PATH, ENTITY_JSON);
            stubJson(SUMMARY_PATH, summaryJson("standard", EXTRACT));

            final Optional<String> description = source.fetch(lookup(QID));

            assertThat(description).contains(EXTRACT);
        }

        @Test
        @DisplayName("a disambiguation summary is rejected")
        void rejectsDisambiguation() {
            stubJson(ENTITY_PATH, ENTITY_JSON);
            stubJson(SUMMARY_PATH, summaryJson("disambiguation", "Dune may refer to:"));

            final Optional<String> description = source.fetch(lookup(QID));

            assertThat(description).isEmpty();
        }

        @Test
        @DisplayName("a missing enwiki sitelink resolves to empty")
        void missingSitelinkIsEmpty() {
            stubJson(ENTITY_PATH, "{ \"entities\": { \"Q190192\": { \"sitelinks\": {} } } }");

            final Optional<String> description = source.fetch(lookup(QID));

            assertThat(description).isEmpty();
        }

        @Test
        @DisplayName("a 404 from the summary resolves to empty")
        void summaryNotFoundIsEmpty() {
            stubJson(ENTITY_PATH, ENTITY_JSON);
            WIREMOCK.stubFor(get(urlPathEqualTo(SUMMARY_PATH))
                .willReturn(aResponse().withStatus(HTTP_NOT_FOUND)));

            final Optional<String> description = source.fetch(lookup(QID));

            assertThat(description).isEmpty();
        }

        @Test
        @DisplayName("a null QID resolves to empty without a call")
        void nullQidIsEmpty() {
            final Optional<String> description = source.fetch(
                new DescriptionLookup(null, ISBN, TITLE, AUTHOR, null, null));

            assertThat(description).isEmpty();
        }
    }

    private static DescriptionLookup lookup(final String qid) {
        return new DescriptionLookup(qid, ISBN, TITLE, AUTHOR, null, null);
    }

    private static void stubJson(final String path, final String body) {
        WIREMOCK.stubFor(get(urlPathEqualTo(path))
            .willReturn(aResponse()
                .withStatus(HTTP_OK)
                .withHeader(CONTENT_TYPE_HEADER, JSON_CONTENT_TYPE)
                .withBody(body)));
    }

    private static String summaryJson(final String type, final String extract) {
        return "{ \"type\": \"" + type + "\", \"extract\": \"" + extract + "\" }";
    }

    private static WireMockServer startServer() {
        final WireMockServer server = new WireMockServer(0);
        server.start();
        return server;
    }
}
