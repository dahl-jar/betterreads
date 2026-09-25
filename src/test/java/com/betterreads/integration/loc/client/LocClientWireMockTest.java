package com.betterreads.integration.loc.client;

import static com.betterreads.integration.loc.LocRecords.sruResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import com.betterreads.catalog.service.source.model.BookFieldSource;
import com.betterreads.catalog.service.source.model.SourceBook;
import com.betterreads.integration.loc.LocProperties;
import com.betterreads.integration.loc.LocSru;
import com.betterreads.integration.loc.LocWebClientConfig;
import com.betterreads.integration.loc.LocRecords;
import com.betterreads.integration.loc.LocWireMock;
import com.betterreads.integration.loc.mapper.LocMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.function.client.WebClientResponseException;

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
class LocClientWireMockTest extends LocWireMock {

    private static final int HTTP_BAD_GATEWAY = 502;

    private static final String DUNE_LCCN = "2019287107";
    private static final String JORDAN = "Robert Jordan";

    @Autowired
    private LocClientImpl client;

    private static LocRecords eyeOfTheWorld() {
        return sruResponse().withNonSort("The ").withTitle("eye of the world").withPrimaryNamePart("Jordan, Robert.");
    }

    @Nested
    @DisplayName("fetchByLccn")
    class FetchByLccn {

        @Test
        @DisplayName("queries the bath.lccn index and maps the returned record")
        void mapsTheRecordForAnLccnQuery() {
            stubSru(sruResponse());

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
            stubSru(sruResponse());

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
            stubSru(sruResponse());

            client.fetchByTitleAuthor("Du\"ne", "Herbert");

            WIREMOCK.verify(sruQueryContaining("bath.title=\"Dune\""));
        }

        @Test
        @DisplayName("keeps the record whose title matches the queried title")
        void keepsAMatchingRecord() {
            final String isbn13 = "9780312850098";
            stubSru(eyeOfTheWorld().withIsbns("0312850093 :", isbn13));

            final Optional<SourceBook> result =
                client.fetchByTitleAuthor("The Eye of the World", JORDAN);

            assertThat(result)
                .isPresent()
                .get()
                .satisfies(book -> assertThat(book.isbn13()).isEqualTo(isbn13));
        }

        @Test
        @DisplayName("rejects a record for a different work that shares the query's keywords")
        void rejectsADriftedRecord() {
            stubSru(eyeOfTheWorld());

            final Optional<SourceBook> result = client.fetchByTitleAuthor(
                "The World of Robert Jordan's The Wheel of Time", JORDAN);

            assertThat(result)
                .as("should reject a keyword match on a different work's record")
                .isEmpty();
        }
    }

    @Nested
    @DisplayName("error handling")
    class ErrorHandling {

        @Test
        @DisplayName("a 404 resolves to empty")
        void notFoundIsEmpty() {
            stubStatus(HTTP_NOT_FOUND);

            assertThat(client.fetchByLccn(DUNE_LCCN)).isEmpty();
        }

        @Test
        @DisplayName("a 502 propagates")
        void serverErrorPropagates() {
            stubStatus(HTTP_BAD_GATEWAY);

            Assertions.assertThatThrownBy(() -> client.fetchByLccn(DUNE_LCCN))
                .isInstanceOf(WebClientResponseException.class);
        }
    }

    private static RequestPatternBuilder sruQueryContaining(final String cql) {
        return WireMock.getRequestedFor(urlPathEqualTo(SRU_PATH))
            .withQueryParam("query", WireMock.containing(cql));
    }
}
