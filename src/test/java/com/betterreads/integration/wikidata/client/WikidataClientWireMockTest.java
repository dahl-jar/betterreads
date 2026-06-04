package com.betterreads.integration.wikidata.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import com.betterreads.catalog.service.source.SourceBook;
import com.betterreads.integration.wikidata.WikidataApi;
import com.betterreads.integration.wikidata.WikidataProperties;
import com.betterreads.integration.wikidata.WikidataWebClientConfig;
import com.betterreads.integration.wikidata.mapper.WikidataMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Exercises the Wikidata client request path against a stubbed boundary, so candidate
 * disambiguation, author enrichment, and the 4xx-to-empty / 5xx-propagates contract run with no
 * live API.
 *
 * <p>The search stub returns the real "Dune" ranking: the film outranks the novel, so the resolver
 * must skip the top hit and pick the literary work.
 */
@SpringBootTest(
    classes = {
        WikidataWebClientConfig.class,
        WikidataApi.class,
        WikidataClientImpl.class,
        WikidataMapper.class
    },
    properties = "spring.main.web-application-type=none"
)
@EnableConfigurationProperties(WikidataProperties.class)
class WikidataClientWireMockTest {

    private static final int CONNECT_TIMEOUT_MS = 2000;
    private static final int READ_TIMEOUT_MS = 5000;
    private static final int HTTP_NOT_FOUND = 404;
    private static final int HTTP_SERVER_ERROR = 503;

    private static final String SEARCH_PATH = "/w/api.php";

    private static final String FILM_QID = "Q60834962";
    private static final String NOVEL_QID = "Q190192";
    private static final String AUTHOR_QID = "Q7934";
    private static final String DUNE_TITLE = "Dune";
    private static final String HERBERT_NAME = "Frank Herbert";

    private static final String DUNE_SEARCH = """
        {"search": [
          {"id": "Q60834962", "label": "Dune", "description": "2021 film"},
          {"id": "Q190192", "label": "Dune", "description": "1965 novel"}
        ]}
        """;

    private static final String FILM_ENTITY = """
        {"entities": {"Q60834962": {
          "id": "Q60834962",
          "labels": {"en": {"value": "Dune"}},
          "claims": {"P31": [{"mainsnak": {"snaktype": "value",
            "datavalue": {"type": "wikibase-entityid", "value": {"id": "Q11424"}}}}]}
        }}}
        """;

    private static final String NOVEL_ENTITY = """
        {"entities": {"Q190192": {
          "id": "Q190192",
          "labels": {"en": {"value": "Dune"}},
          "sitelinks": {"enwiki": {"title": "Dune (novel)"}},
          "claims": {
            "P31": [{"mainsnak": {"snaktype": "value",
              "datavalue": {"type": "wikibase-entityid", "value": {"id": "Q7725634"}}}}],
            "P50": [{"mainsnak": {"snaktype": "value",
              "datavalue": {"type": "wikibase-entityid", "value": {"id": "Q7934"}}}}],
            "P577": [{"rank": "normal", "mainsnak": {"snaktype": "value",
              "datavalue": {"type": "time", "value": {"time": "+1965-00-00T00:00:00Z"}}}}],
            "P648": [{"mainsnak": {"snaktype": "value",
              "datavalue": {"type": "string", "value": "OL893527W"}}}]
          }
        }}}
        """;

    private static final String AUTHOR_ENTITY = """
        {"entities": {"Q7934": {
          "id": "Q7934",
          "labels": {},
          "sitelinks": {"enwiki": {
            "title": "Frank Herbert", "url": "https://en.wikipedia.org/wiki/Frank_Herbert"}},
          "claims": {"P18": [{"mainsnak": {"snaktype": "value",
            "datavalue": {"type": "string", "value": "Frank Herbert 1984.jpg"}}}]}
        }}}
        """;

    private static final String DRIFT_QID = "Q1138110";

    private static final String DRIFT_SEARCH = """
        {"search": [{"id": "Q1138110", "label": "The Santaroga Barrier"}]}
        """;

    private static final String DRIFT_ENTITY = """
        {"entities": {"Q1138110": {
          "id": "Q1138110",
          "labels": {"en": {"value": "The Santaroga Barrier"}},
          "claims": {
            "P31": [{"mainsnak": {"snaktype": "value",
              "datavalue": {"type": "wikibase-entityid", "value": {"id": "Q7725634"}}}}],
            "P50": [{"mainsnak": {"snaktype": "value",
              "datavalue": {"type": "wikibase-entityid", "value": {"id": "Q7934"}}}}]
          }
        }}}
        """;

    private static final WireMockServer WIREMOCK = startServer();

    @Autowired
    private WikidataClientImpl client;

    private static WireMockServer startServer() {
        final WireMockServer server = new WireMockServer(0);
        server.start();
        return server;
    }

    private static String entityPath(final String qid) {
        return "/wiki/Special:EntityData/" + qid + ".json";
    }

    private static void stubEntity(final String qid, final String body) {
        WIREMOCK.stubFor(get(urlPathEqualTo(entityPath(qid))).willReturn(json(body)));
    }

    private static void stubDuneResolution() {
        WIREMOCK.stubFor(get(urlPathEqualTo(SEARCH_PATH)).willReturn(json(DUNE_SEARCH)));
        stubEntity(FILM_QID, FILM_ENTITY);
        stubEntity(NOVEL_QID, NOVEL_ENTITY);
        stubEntity(AUTHOR_QID, AUTHOR_ENTITY);
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
    static void wikidataProperties(final DynamicPropertyRegistry registry) {
        registry.add("wikidata.base-url", () -> "http://localhost:" + WIREMOCK.port());
        registry.add("wikidata.connect-timeout", () -> CONNECT_TIMEOUT_MS);
        registry.add("wikidata.read-timeout", () -> READ_TIMEOUT_MS);
    }

    @Nested
    class FetchByTitleAuthor {

        @Test
        void picksTheNovelOverTheFilmThatOutranksIt() {
            stubDuneResolution();

            final Optional<SourceBook> result = client.fetchByTitleAuthor(DUNE_TITLE, HERBERT_NAME);

            assertThat(result).isPresent().get().satisfies(book -> {
                assertThat(book.wikidataQid()).isEqualTo(NOVEL_QID);
                assertThat(book.openLibraryWorkKey()).isEqualTo("OL893527W");
            });
        }

        @Test
        void readsTheAuthorPhotoAndBioFromTheAuthorEntity() {
            stubDuneResolution();

            final SourceBook book = client.fetchByTitleAuthor(DUNE_TITLE, HERBERT_NAME).orElseThrow();

            assertThat(book.authors())
                .singleElement()
                .satisfies(author -> {
                    assertThat(author.name()).isEqualTo(HERBERT_NAME);
                    assertThat(author.photoUrl())
                        .isEqualTo("https://commons.wikimedia.org/wiki/Special:FilePath/Frank Herbert 1984.jpg");
                    assertThat(author.bio()).isEqualTo("https://en.wikipedia.org/wiki/Frank_Herbert");
                });
        }

        @Test
        void returnsEmptyWhenNoCandidateMatchesTheAuthor() {
            WIREMOCK.stubFor(get(urlPathEqualTo(SEARCH_PATH)).willReturn(json(DUNE_SEARCH)));
            stubEntity(FILM_QID, FILM_ENTITY);
            stubEntity(NOVEL_QID, NOVEL_ENTITY);

            final Optional<SourceBook> result = client.fetchByTitleAuthor(DUNE_TITLE, "Ursula K. Le Guin");

            assertThat(result).isEmpty();
        }

        @Test
        void rejectsASameAuthorWorkWhoseTitleDriftsFromTheQuery() {
            WIREMOCK.stubFor(get(urlPathEqualTo(SEARCH_PATH)).willReturn(json(DRIFT_SEARCH)));
            stubEntity(DRIFT_QID, DRIFT_ENTITY);

            final Optional<SourceBook> result = client.fetchByTitleAuthor(DUNE_TITLE, HERBERT_NAME);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    class FetchByIsbn {

        @Test
        void returnsEmptyBecauseWorksCarryNoIsbn() {
            final Optional<SourceBook> result = client.fetchByIsbn("9780441013593");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    class FetchByQid {

        @Test
        void mapsTheEntityDirectlyWithoutSearching() {
            stubEntity(NOVEL_QID, NOVEL_ENTITY);
            stubEntity(AUTHOR_QID, AUTHOR_ENTITY);

            final Optional<SourceBook> result = client.fetchByQid(NOVEL_QID);

            assertThat(result).isPresent().get()
                .extracting(SourceBook::title).isEqualTo(DUNE_TITLE);
        }

        @Test
        void returnsEmptyWhenTheQidIsNotAWrittenWork() {
            stubEntity(FILM_QID, FILM_ENTITY);

            assertThat(client.fetchByQid(FILM_QID)).isEmpty();
        }
    }

    @Nested
    class Failures {

        @Test
        void returnsEmptyWhenTheEntityIs404() {
            WIREMOCK.stubFor(get(urlPathEqualTo(entityPath(NOVEL_QID)))
                .willReturn(aResponse().withStatus(HTTP_NOT_FOUND)));

            assertThat(client.fetchByQid(NOVEL_QID)).isEmpty();
        }

        @Test
        void propagatesWhenTheEntityIs5xx() {
            WIREMOCK.stubFor(get(urlPathEqualTo(entityPath(NOVEL_QID)))
                .willReturn(aResponse().withStatus(HTTP_SERVER_ERROR)));

            Assertions.assertThatThrownBy(() -> client.fetchByQid(NOVEL_QID))
                .isInstanceOf(WebClientResponseException.class);
        }
    }
}
