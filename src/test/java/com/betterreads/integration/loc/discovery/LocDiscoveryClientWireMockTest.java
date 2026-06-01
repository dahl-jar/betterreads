package com.betterreads.integration.loc.discovery;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import com.betterreads.integration.loc.LocProperties;
import com.betterreads.integration.loc.LocSru;
import com.betterreads.integration.loc.LocWebClientConfig;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Exercises the LoC discovery walk against a stubbed SRU boundary.
 *
 * <p>The stub body is a real marcxml response with one record cataloged 2024-01-29, one record with
 * no cataloging date, and one cataloged 2023-09-01, so the recency filter and the skip-an-incomplete
 * -record behavior both run against the shape the live endpoint returns.
 */
@SpringBootTest(
    classes = {
        LocWebClientConfig.class,
        LocSru.class,
        LocDiscoveryClientImpl.class
    },
    properties = "spring.main.web-application-type=none"
)
@EnableConfigurationProperties(LocProperties.class)
class LocDiscoveryClientWireMockTest {

    private static final int CONNECT_TIMEOUT_MS = 2000;
    private static final int READ_TIMEOUT_MS = 5000;

    private static final String SRU_PATH = "/lcdb";
    private static final String QUERY_PARAM = "query";
    private static final int YEAR = 2024;
    private static final String SCI_FI = "science fiction";

    private static final LocalDate RECENT_RECORD_DATE = LocalDate.of(2024, 1, 29);
    private static final LocalDate OLD_RECORD_DATE = LocalDate.of(2023, 9, 1);

    private static final String START_RECORD_PARAM = "startRecord";

    private static final WireMockServer WIREMOCK = startServer();

    private static final String MIXED_MARCXML = fixture("integration/loc/discovery-mixed-marcxml.xml");
    private static final String PAGE_1_MARCXML = fixture("integration/loc/discovery-page1-marcxml.xml");
    private static final String PAGE_2_MARCXML = fixture("integration/loc/discovery-page2-marcxml.xml");

    @Autowired
    private LocDiscoveryClientImpl client;

    private static WireMockServer startServer() {
        final WireMockServer server = new WireMockServer(0);
        server.start();
        return server;
    }

    private static String fixture(final String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
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
    @DisplayName("discoverByDate")
    class DiscoverByDate {

        @Test
        @DisplayName("queries marcxml with the year and subject bucket")
        void queriesMarcxmlForTheYearAndBucket() {
            WIREMOCK.stubFor(get(urlPathEqualTo(SRU_PATH)).willReturn(xml(MIXED_MARCXML)));

            client.discoverByDate(YEAR, SCI_FI, OLD_RECORD_DATE);

            WIREMOCK.verify(WireMock.getRequestedFor(urlPathEqualTo(SRU_PATH))
                .withQueryParam("recordSchema", WireMock.equalTo("marcxml"))
                .withQueryParam(QUERY_PARAM, WireMock.containing("dc.date=" + YEAR))
                .withQueryParam(QUERY_PARAM, WireMock.containing("bath.subject=\"" + SCI_FI + "\"")));
        }

        @Test
        @DisplayName("parses each record's lccn, isbn-13, title, and 008 cataloging date")
        void parsesDiscoveryFields() {
            WIREMOCK.stubFor(get(urlPathEqualTo(SRU_PATH)).willReturn(xml(MIXED_MARCXML)));

            final List<LocDiscoveryRecord> found = client.discoverByDate(YEAR, SCI_FI, OLD_RECORD_DATE);

            assertThat(found)
                .anySatisfy(record -> {
                    assertThat(record.lccn()).isEqualTo("2023042683");
                    assertThat(record.isbn13()).isEqualTo("9781478030683");
                    assertThat(record.title()).isEqualTo("Tomorrowing");
                    assertThat(record.catalogedOn()).isEqualTo(RECENT_RECORD_DATE);
                });
        }

        @Test
        @DisplayName("pages past the first request so records beyond one page are not dropped")
        void pagesThroughAllResults() {
            WIREMOCK.stubFor(get(urlPathEqualTo(SRU_PATH))
                .withQueryParam(START_RECORD_PARAM, WireMock.equalTo("1"))
                .willReturn(xml(PAGE_1_MARCXML)));
            WIREMOCK.stubFor(get(urlPathEqualTo(SRU_PATH))
                .withQueryParam(START_RECORD_PARAM, WireMock.equalTo("3"))
                .willReturn(xml(PAGE_2_MARCXML)));

            final List<LocDiscoveryRecord> found = client.discoverByDate(YEAR, SCI_FI, OLD_RECORD_DATE);

            assertThat(found)
                .extracting(LocDiscoveryRecord::lccn)
                .as("the third record lives on page two; a single-page walk would miss it")
                .containsExactlyInAnyOrder("1000000001", "1000000002", "1000000003");
        }

        @Test
        @DisplayName("drops records cataloged before the cutoff and ones with no cataloging date")
        void keepsRecentSkipsOldAndUndated() {
            WIREMOCK.stubFor(get(urlPathEqualTo(SRU_PATH)).willReturn(xml(MIXED_MARCXML)));

            final LocalDate cutoff = LocalDate.of(2024, 1, 1);
            final List<LocDiscoveryRecord> found = client.discoverByDate(YEAR, SCI_FI, cutoff);

            assertThat(found)
                .as("the 2024-01-29 record survives; the 2023-09-01 and the undated one drop")
                .extracting(LocDiscoveryRecord::catalogedOn)
                .containsExactly(RECENT_RECORD_DATE);
        }
    }
}
