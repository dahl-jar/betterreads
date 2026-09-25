package com.betterreads.integration.wikipedia;

import static com.betterreads.integration.wikipedia.PageSummaryJson.pageSummary;
import static com.betterreads.integration.wikidata.WikidataEntityJson.FIXTURE_QID;
import static com.betterreads.integration.wikidata.WikidataEntityJson.entity;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import com.betterreads.catalog.service.source.port.DescriptionLookup;
import com.betterreads.integration.wikidata.WikidataApi;
import com.betterreads.integration.wikidata.WikidataProperties;
import com.betterreads.integration.wikidata.WikidataWebClientConfig;
import com.betterreads.integration.wikidata.WikidataWireMock;
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
        WikidataWebClientConfig.class,
        WikipediaWebClientConfig.class,
        WikidataApi.class,
        WikipediaApi.class,
        WikipediaDescriptionSource.class
    },
    properties = "spring.main.web-application-type=none"
)
@EnableConfigurationProperties({WikidataProperties.class, WikipediaProperties.class})
class WikipediaDescriptionSourceWireMockTest extends WikidataWireMock {

    private static final String ISBN = "9780441013593";

    private static final String TITLE = "Dune";

    private static final String AUTHOR = "Frank Herbert";

    private static final String SUMMARY_PATH = "/api/rest_v1/page/summary/Dune_(novel)";

    private static final String EXTRACT =
        "Dune is a 1965 epic science fiction novel by American author Frank Herbert, the first in a "
        + "long series set on the desert planet Arrakis.";

    @Autowired
    private WikipediaDescriptionSource source;

    @DynamicPropertySource
    static void wireProperties(final DynamicPropertyRegistry registry) {
        registerSource(registry, "wikipedia", "");
    }

    @Nested
    @DisplayName("fetch")
    class Fetch {

        @Test
        @DisplayName("resolves the enwiki sitelink and returns the summary extract")
        void returnsExtract() {
            stubEntity(entity());
            stubSummary(pageSummary());

            final Optional<String> description = source.fetch(lookup(FIXTURE_QID));

            assertThat(description).contains(EXTRACT);
        }

        @Test
        @DisplayName("a disambiguation summary is rejected")
        void rejectsDisambiguation() {
            stubEntity(entity());
            stubSummary(pageSummary().asDisambiguation("Dune may refer to:"));

            final Optional<String> description = source.fetch(lookup(FIXTURE_QID));

            assertThat(description).isEmpty();
        }

        @Test
        @DisplayName("a missing enwiki sitelink resolves to empty")
        void missingSitelinkIsEmpty() {
            stubEntity(entity().withoutSitelinks());

            final Optional<String> description = source.fetch(lookup(FIXTURE_QID));

            assertThat(description).isEmpty();
        }

        @Test
        @DisplayName("a 404 from the summary resolves to empty")
        void summaryNotFoundIsEmpty() {
            stubEntity(entity());
            WIREMOCK.stubFor(get(urlPathEqualTo(SUMMARY_PATH))
                .willReturn(aResponse().withStatus(HTTP_NOT_FOUND)));

            final Optional<String> description = source.fetch(lookup(FIXTURE_QID));

            assertThat(description).isEmpty();
        }

        @Test
        @DisplayName("a null QID resolves to empty without a call")
        void nullQidIsEmpty() {
            final Optional<String> description = source.fetch(
                new DescriptionLookup(null, ISBN, TITLE, AUTHOR, null, null));

            assertThat(description).isEmpty();
            WIREMOCK.verify(0, getRequestedFor(anyUrl()));
        }
    }

    private static DescriptionLookup lookup(final String qid) {
        return new DescriptionLookup(qid, ISBN, TITLE, AUTHOR, null, null);
    }

    private static void stubSummary(final PageSummaryJson summary) {
        WIREMOCK.stubFor(get(urlPathEqualTo(SUMMARY_PATH)).willReturn(okJson(summary.toString())));
    }
}
