package com.betterreads.integration.loc.discovery;

import static com.betterreads.integration.loc.LocMarcRecords.marcResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import com.betterreads.integration.loc.LocProperties;
import com.betterreads.integration.loc.LocSru;
import com.betterreads.integration.loc.LocWebClientConfig;
import com.betterreads.integration.loc.LocWireMock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = {
        LocWebClientConfig.class,
        LocSru.class,
        LocDiscoveryClientImpl.class
    },
    properties = "spring.main.web-application-type=none"
)
@EnableConfigurationProperties(LocProperties.class)
class LocDiscoveryClientWireMockTest extends LocWireMock {

    private static final int YEAR = 2024;

    private static final String QUERY_PARAM = "query";

    private static final String FIXTURE_LCCN = "2023042683";

    private static final String SUBJECT = "science fiction";

    private static final LocalDate CATALOGED_SINCE = LocalDate.of(YEAR, 1, 1);

    private static final int TOTAL_RECORDS = 3;

    private static final int NEXT_PAGE_START = 3;

    @Autowired
    private LocDiscoveryClientImpl client;

    private List<LocDiscoveryRecord> discover() {
        return client.discoverByDate(YEAR, SUBJECT, CATALOGED_SINCE);
    }

    @Nested
    @DisplayName("discoverByDate")
    class DiscoverByDate {

        @Test
        void shouldQueryMarcxmlForTheYearAndSubject() {
            stubSru(marcResponse());

            discover();

            WIREMOCK.verify(getRequestedFor(urlPathEqualTo(SRU_PATH))
                .withQueryParam("recordSchema", equalTo("marcxml"))
                .withQueryParam(QUERY_PARAM, containing("dc.date=" + YEAR))
                .withQueryParam(QUERY_PARAM, containing("bath.subject=\"" + SUBJECT + "\"")));
        }

        @Test
        void shouldReadTheDiscoveryRecord() {
            stubSru(marcResponse());

            final List<LocDiscoveryRecord> found = discover();

            assertThat(found).containsExactly(new LocDiscoveryRecord(
                FIXTURE_LCCN, "9781478030683", "Tomorrowing", LocalDate.parse("2024-01-29")));
        }

        @Test
        void shouldFetchTheNextPageWhenTheTotalExceedsTheFirstPage() {
            final String secondLccn = "1000000002";
            final String nextPageLccn = "1000000003";
            stubSruPage(1, marcResponse().withRecord(secondLccn, "Morning star").withTotal(TOTAL_RECORDS));
            stubSruPage(NEXT_PAGE_START, marcResponse().withoutRecords().withRecord(nextPageLccn, "Golden son"));

            final List<LocDiscoveryRecord> found = discover();

            assertThat(found).extracting(LocDiscoveryRecord::lccn)
                .containsExactly(FIXTURE_LCCN, secondLccn, nextPageLccn);
        }

        @Test
        void shouldDropRecordsCatalogedBeforeTheCutoff() {
            stubSru(marcResponse().withoutRecords()
                .withRecord("2023945886", "The state of the art", "230901t20241991nyua          000 j eng d"));

            final List<LocDiscoveryRecord> found = discover();

            assertThat(found).isEmpty();
        }

        @Test
        void shouldSkipRecordsWithoutACatalogingDate() {
            stubSru(marcResponse().withoutRecords().withRecord("9999999999", "Iron gold", null));

            final List<LocDiscoveryRecord> found = discover();

            assertThat(found).isEmpty();
        }
    }
}
