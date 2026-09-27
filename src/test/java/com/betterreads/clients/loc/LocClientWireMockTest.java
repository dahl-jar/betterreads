package com.betterreads.clients.loc;

import com.betterreads.booksource.SourceBook;
import static com.betterreads.clients.loc.LocRecords.DUNE_ISBN;
import static com.betterreads.clients.loc.LocRecords.sruResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

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

    private static final String JORDAN = "Robert Jordan";

    @Autowired
    private LocClientImpl client;

    private static LocRecords eyeOfTheWorld() {
        return sruResponse().withNonSort("The ").withTitle("eye of the world").withPrimaryNamePart("Jordan, Robert.");
    }

    @Nested
    @DisplayName("fetchByIsbn")
    class FetchByIsbn {

        @Test
        @DisplayName("queries the bath.isbn index")
        void queriesTheIsbnIndex() {
            stubSru(sruResponse());

            final Optional<SourceBook> result = client.fetchByIsbn(DUNE_ISBN);

            assertThat(result).isPresent();
            WIREMOCK.verify(sruQueryContaining("bath.isbn=" + DUNE_ISBN));
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

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("error handling")
    class ErrorHandling {

        @Test
        @DisplayName("a 404 resolves to empty")
        void notFoundIsEmpty() {
            stubStatus(HTTP_NOT_FOUND);

            final Optional<SourceBook> result = client.fetchByIsbn(DUNE_ISBN);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("a 502 propagates")
        void serverErrorPropagates() {
            stubStatus(HTTP_BAD_GATEWAY);

            Assertions.assertThatThrownBy(() -> client.fetchByIsbn(DUNE_ISBN))
                .isInstanceOf(WebClientResponseException.class);
        }
    }

    private static RequestPatternBuilder sruQueryContaining(final String cql) {
        return WireMock.getRequestedFor(urlPathEqualTo(SRU_PATH))
            .withQueryParam("query", WireMock.containing(cql));
    }
}
