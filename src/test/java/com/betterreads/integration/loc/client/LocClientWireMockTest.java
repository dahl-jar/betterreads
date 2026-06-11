package com.betterreads.integration.loc.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import com.betterreads.catalog.service.source.BookFieldSource;
import com.betterreads.catalog.service.source.SourceBook;
import com.betterreads.integration.loc.LocProperties;
import com.betterreads.integration.loc.LocSru;
import com.betterreads.integration.loc.LocWebClientConfig;
import com.betterreads.integration.loc.mapper.LocMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import org.assertj.core.api.Assertions;
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
 * Exercises the LoC SRU request path against a stubbed HTTP boundary, so query building, the
 * MODS-to-SourceBook map, and the 4xx-to-empty / 5xx-propagates contract run in CI with no live
 * endpoint.
 *
 * <p>The stub body is an inline Dune SRU response, so the test fails if the client stops handing the
 * raw XML to the mapper.
 */
@SpringBootTest(
    classes = {
        LocWebClientConfig.class,
        LocSru.class,
        LocClientImpl.class,
        LocMapper.class
    },
    properties = "spring.main.web-application-type=none"
)
@EnableConfigurationProperties(LocProperties.class)
class LocClientWireMockTest {

    private static final int CONNECT_TIMEOUT_MS = 2000;
    private static final int READ_TIMEOUT_MS = 5000;
    private static final int HTTP_NOT_FOUND = 404;
    private static final int HTTP_BAD_GATEWAY = 502;

    private static final String SRU_PATH = "/lcdb";
    private static final String DUNE_LCCN = "2019287107";
    private static final String JORDAN = "Robert Jordan";

    private static final WireMockServer WIREMOCK = startServer();

    private static final String DUNE_SRU = """
        <?xml version="1.0"?>
        <zs:searchRetrieveResponse xmlns:zs="http://www.loc.gov/zing/srw/"><zs:records><zs:record>\
        <zs:recordData><mods xmlns="http://www.loc.gov/mods/v3" version="3.8">
        <titleInfo><title>Dune</title></titleInfo>
        <name type="personal" usage="primary"><namePart>Herbert, Frank,</namePart></name>
        <identifier type="isbn">9780593099322</identifier>
        <identifier type="lccn">2019287107</identifier>
        </mods></zs:recordData></zs:record></zs:records></zs:searchRetrieveResponse>""";

    private static final String EYE_SRU = """
        <?xml version="1.0"?>
        <zs:searchRetrieveResponse xmlns:zs="http://www.loc.gov/zing/srw/"><zs:records><zs:record>\
        <zs:recordData><mods xmlns="http://www.loc.gov/mods/v3" version="3.8">
        <titleInfo><nonSort xml:space="preserve">The </nonSort><title>eye of the world</title></titleInfo>
        <name type="personal" usage="primary"><namePart>Jordan, Robert,</namePart></name>
        <identifier type="isbn">9780312850098</identifier>
        <identifier type="lccn">89007939</identifier>
        </mods></zs:recordData></zs:record></zs:records></zs:searchRetrieveResponse>""";

    @Autowired
    private LocClientImpl client;

    private static WireMockServer startServer() {
        final WireMockServer server = new WireMockServer(0);
        server.start();
        return server;
    }

    private static ResponseDefinitionBuilder xml(final String body) {
        return aResponse().withHeader("Content-Type", "application/xml").withBody(body);
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
    static void locProperties(final DynamicPropertyRegistry registry) {
        registry.add("loc.base-url", () -> "http://localhost:" + WIREMOCK.port() + SRU_PATH);
        registry.add("loc.connect-timeout", () -> CONNECT_TIMEOUT_MS);
        registry.add("loc.read-timeout", () -> READ_TIMEOUT_MS);
    }

    @Nested
    @DisplayName("fetchByLccn")
    class FetchByLccn {

        @Test
        @DisplayName("queries the bath.lccn index and maps the returned record")
        void mapsTheRecordForAnLccnQuery() {
            WIREMOCK.stubFor(get(urlPathEqualTo(SRU_PATH)).willReturn(xml(DUNE_SRU)));

            assertThat(client.fetchByLccn(DUNE_LCCN))
                .isPresent()
                .get()
                .satisfies(book -> {
                    assertThat(book.source()).isEqualTo(BookFieldSource.LOC);
                    assertThat(book.locLccn()).isEqualTo(DUNE_LCCN);
                });

            WIREMOCK.verify(sruQueryContaining("bath.lccn=" + DUNE_LCCN));
        }
    }

    @Nested
    @DisplayName("fetchByIsbn")
    class FetchByIsbn {

        @Test
        @DisplayName("queries the bath.isbn index")
        void queriesTheIsbnIndex() {
            WIREMOCK.stubFor(get(urlPathEqualTo(SRU_PATH)).willReturn(xml(DUNE_SRU)));

            assertThat(client.fetchByIsbn("9780593099322")).isPresent();

            WIREMOCK.verify(sruQueryContaining("bath.isbn=9780593099322"));
        }
    }

    @Nested
    @DisplayName("fetchByTitleAuthor")
    class FetchByTitleAuthor {

        @Test
        @DisplayName("strips embedded quotes from the title so the CQL stays well-formed")
        void stripsQuotesFromTitle() {
            WIREMOCK.stubFor(get(urlPathEqualTo(SRU_PATH)).willReturn(xml(DUNE_SRU)));

            client.fetchByTitleAuthor("Du\"ne", "Herbert");

            WIREMOCK.verify(sruQueryContaining("bath.title=\"Dune\""));
        }

        @Test
        @DisplayName("keeps the record whose title matches the queried title")
        void keepsAMatchingRecord() {
            WIREMOCK.stubFor(get(urlPathEqualTo(SRU_PATH)).willReturn(xml(EYE_SRU)));

            final Optional<SourceBook> result =
                client.fetchByTitleAuthor("The Eye of the World", JORDAN);

            assertThat(result)
                .isPresent()
                .get()
                .satisfies(book -> assertThat(book.isbn13()).isEqualTo("9780312850098"));
        }

        @Test
        @DisplayName("rejects a record for a different work that shares the query's keywords")
        void rejectsADriftedRecord() {
            WIREMOCK.stubFor(get(urlPathEqualTo(SRU_PATH)).willReturn(xml(EYE_SRU)));

            final Optional<SourceBook> result = client.fetchByTitleAuthor(
                "The World of Robert Jordan's The Wheel of Time", JORDAN);

            assertThat(result)
                .as("a keyword match on a different work's record must not attach its identifiers")
                .isEmpty();
        }
    }

    @Nested
    @DisplayName("error handling")
    class ErrorHandling {

        @Test
        @DisplayName("a 404 resolves to empty")
        void notFoundIsEmpty() {
            WIREMOCK.stubFor(get(urlPathEqualTo(SRU_PATH))
                .willReturn(aResponse().withStatus(HTTP_NOT_FOUND)));

            assertThat(client.fetchByLccn(DUNE_LCCN)).isEmpty();
        }

        @Test
        @DisplayName("a 502 propagates rather than resolving to empty")
        void serverErrorPropagates() {
            WIREMOCK.stubFor(get(urlPathEqualTo(SRU_PATH))
                .willReturn(aResponse().withStatus(HTTP_BAD_GATEWAY)));

            Assertions.assertThatThrownBy(() -> client.fetchByLccn(DUNE_LCCN))
                .isInstanceOf(WebClientResponseException.class);
        }
    }

    private static RequestPatternBuilder sruQueryContaining(final String cql) {
        return WireMock.getRequestedFor(urlPathEqualTo(SRU_PATH))
            .withQueryParam("query", WireMock.containing(cql));
    }
}
