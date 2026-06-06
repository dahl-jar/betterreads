package com.betterreads.integration.hardcover.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.betterreads.catalog.service.source.SourceSeries;
import com.betterreads.catalog.service.source.SourceSeriesVolume;
import com.betterreads.integration.hardcover.HardcoverProperties;
import com.betterreads.integration.hardcover.HardcoverWebClientConfig;
import com.betterreads.integration.hardcover.mapper.HardcoverSeriesMapper;
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
 * Exercises the Hardcover series client against a stubbed GraphQL boundary. The search call and the
 * enumeration call hit the same path, so the stub matches on the GraphQL query text to return the
 * series-search payload for one and the volume payload for the other.
 *
 * <p>The volume payload puts each collapse rule on its own row so a single assertion can prove which
 * rule fired: position 0 is a prequel, position 1 carries a boxed set ahead of the novel, position 2
 * carries a non-English edition ahead of the English one, position 3 is non-English only, and
 * position 4 sits past the series' three-book count.
 */
@SpringBootTest(
    classes = {
        HardcoverWebClientConfig.class,
        HardcoverSeriesClientImpl.class,
        HardcoverSeriesMapper.class
    },
    properties = "spring.main.web-application-type=none"
)
@EnableConfigurationProperties(HardcoverProperties.class)
class HardcoverSeriesClientWireMockTest {

    private static final int CONNECT_TIMEOUT_MS = 2000;

    private static final int READ_TIMEOUT_MS = 5000;

    private static final int HTTP_UNAUTHORIZED = 401;

    private static final int HTTP_SERVER_ERROR = 503;

    private static final String GRAPHQL_PATH = "/v1/graphql";

    private static final String SEARCH_MARKER = "query_type: \\\"Series\\\"";

    private static final String ENUM_MARKER = "book_series";

    private static final String QUERY = "the wheel of time";

    private static final int FIRST_POSITION = 1;

    private static final int SECOND_POSITION = 2;

    private static final int THIRD_POSITION = 3;

    private static final String SERIES_NAME = "The Wheel of Time";

    private static final String EYE = "The Eye of the World";

    private static final String GREAT_HUNT = "The Great Hunt";

    private static final String GN_QUERY = "the sandman";

    private static final String GN_SEARCH_JSON = """
        {"data": {"search": {"results": {"hits": [
          {"document": {"id": "1057", "name": "The Sandman",
            "author_name": "Neil Gaiman", "primary_books_count": 1, "readers_count": 5000}}
        ]}}}}
        """;

    /** Position 1 has a single graphic-novel issue and the collected volume; only the volume stays. */
    private static final String GN_ENUM_JSON = """
        {"data": {"series": [{
          "id": 1057, "name": "The Sandman", "primary_books_count": 1,
          "book_series": [
            {"position": 1, "book": {"title": "The Sandman #1: Sleep of the Just",
              "book_category_id": 4, "compilation": false,
              "default_physical_edition": {"language": {"language": "English"}},
              "contributions": [{"author": {"name": "Neil Gaiman"}}]}},
            {"position": 1, "book": {"title": "The Sandman, Vol. 1: Preludes & Nocturnes",
              "book_category_id": 4, "compilation": true,
              "default_physical_edition": {"language": {"language": "English"}},
              "contributions": [{"author": {"name": "Neil Gaiman"}}]}}
          ]
        }]}}
        """;

    private static final String SEARCH_JSON = """
        {"data": {"search": {"results": {"hits": [
          {"document": {"id": "404", "name": "Wheel of Time Parody",
            "author_name": "Imposter", "primary_books_count": 2, "readers_count": 10}},
          {"document": {"id": "1097", "name": "The Wheel of Time",
            "author_name": "Robert Jordan", "primary_books_count": 3, "readers_count": 23085}}
        ]}}}}
        """;

    private static final String ENUM_JSON = """
        {"data": {"series": [{
          "id": 1097, "name": "The Wheel of Time", "primary_books_count": 3,
          "book_series": [
            {"position": 0, "book": {"title": "New Spring", "users_count": 500,
              "default_physical_edition": {"reading_format": {"format": "Read"},
                "language": {"language": "English"}},
              "contributions": [{"author": {"name": "Robert Jordan"}}]}},
            {"position": 1, "book": {"title": "The Eye of the World: Audiobook", "users_count": 30,
              "default_physical_edition": {"reading_format": {"format": "Listened"},
                "language": {"language": "English"}},
              "contributions": [{"author": {"name": "Robert Jordan"}}]}},
            {"position": 1, "book": {"title": "The Wheel of Time: Boxed Set #1", "users_count": 40,
              "default_physical_edition": {"reading_format": {"format": "Read"},
                "language": {"language": "English"}},
              "contributions": [{"author": {"name": "Robert Jordan"}}]}},
            {"position": 1, "book": {"title": "The Eye of the World: The Deluxe Edition",
              "users_count": 99000,
              "default_physical_edition": {"reading_format": {"format": "Read"},
                "language": {"language": "English"}},
              "contributions": [{"author": {"name": "Robert Jordan"}}]}},
            {"position": 1, "book": {"title": "The Eye of the World, Part 1", "users_count": 80000,
              "is_partial_book": true,
              "default_physical_edition": {"reading_format": {"format": "Read"},
                "language": {"language": "English"}},
              "contributions": [{"author": {"name": "Robert Jordan"}}]}},
            {"position": 1, "book": {"title": "The Wheel of Time Trilogy", "users_count": 70000,
              "book_category_id": 8,
              "default_physical_edition": {"reading_format": {"format": "Read"},
                "language": {"language": "English"}},
              "contributions": [{"author": {"name": "Robert Jordan"}}]}},
            {"position": 1, "book": {"title": "The Eye of the World", "users_count": 9000,
              "default_physical_edition": {"reading_format": {"format": "Read"},
                "language": {"language": "English"}},
              "contributions": [{"author": {"name": "Robert Jordan"}}]}},
            {"position": 2, "book": {"title": "Oko Świata", "users_count": 100,
              "default_physical_edition": {"reading_format": {"format": "Read"},
                "language": {"language": "Polish"}},
              "contributions": [{"author": {"name": "Robert Jordan"}}]}},
            {"position": 2, "book": {"title": "The Great Hunt", "users_count": 7000,
              "default_physical_edition": {"reading_format": {"format": "Read"},
                "language": {"language": "English"}},
              "contributions": [{"author": {"name": "Robert Jordan"}}]}},
            {"position": 3, "book": {"title": "Smok Odrodzony", "users_count": 50,
              "default_physical_edition": {"reading_format": {"format": "Read"},
                "language": {"language": "Polish"}},
              "contributions": [{"author": {"name": "Robert Jordan"}}]}},
            {"position": 4, "book": {"title": "A Crown of Swords", "users_count": 6000,
              "default_physical_edition": {"reading_format": {"format": "Read"},
                "language": {"language": "English"}},
              "contributions": [{"author": {"name": "Robert Jordan"}}]}}
          ]
        }]}}
        """;

    private static final WireMockServer WIREMOCK = startServer();

    @Autowired
    private HardcoverSeriesClientImpl client;

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

    private void stubSearchAndEnum() {
        stub(SEARCH_JSON, ENUM_JSON);
    }

    private void stub(final String searchBody, final String enumBody) {
        WIREMOCK.stubFor(post(urlPathEqualTo(GRAPHQL_PATH))
            .withRequestBody(containing(SEARCH_MARKER)).willReturn(json(searchBody)));
        WIREMOCK.stubFor(post(urlPathEqualTo(GRAPHQL_PATH))
            .withRequestBody(containing(ENUM_MARKER)).willReturn(json(enumBody)));
    }

    @Nested
    @DisplayName("candidate selection")
    class CandidateSelection {

        @Test
        @DisplayName("picks the series with the most readers, not search rank 0")
        void picksHighestReaderSeriesNotRankZero() {
            stubSearchAndEnum();

            final SourceSeries series = client.fetchSeries(QUERY).orElseThrow();

            assertThat(series.name()).isEqualTo(SERIES_NAME);
            assertThat(series.author()).isEqualTo("Robert Jordan");
        }

        @Test
        @DisplayName("no search hit resolves to empty")
        void noHitIsEmpty() {
            WIREMOCK.stubFor(post(urlPathEqualTo(GRAPHQL_PATH)).withRequestBody(containing(SEARCH_MARKER))
                .willReturn(json("{\"data\": {\"search\": {\"results\": {\"hits\": []}}}}")));

            assertThat(client.fetchSeries(QUERY)).isEmpty();
        }

        @Test
        @DisplayName("a zero-book container series is rejected so a standalone title is not staged as a series")
        void zeroBookSeriesRejected() {
            final String zeroBookSeries = """
                {"data": {"search": {"results": {"hits": [
                  {"document": {"id": "999", "name": "George Orwell - 1984",
                    "author_name": "George Orwell", "primary_books_count": 0, "readers_count": 0}}
                ]}}}}
                """;
            WIREMOCK.stubFor(post(urlPathEqualTo(GRAPHQL_PATH)).withRequestBody(containing(SEARCH_MARKER))
                .willReturn(json(zeroBookSeries)));

            assertThat(client.fetchSeries(QUERY))
                .as("a 0-book 'series' is a container, not a real series, so it must not resolve")
                .isEmpty();
        }
    }

    @Nested
    @DisplayName("volume collapse")
    class VolumeCollapse {

        @Test
        @DisplayName("at one position the non-English edition is skipped for the English one")
        void skipsNonEnglishForEnglish() {
            stubSearchAndEnum();

            final SourceSeries series = client.fetchSeries(QUERY).orElseThrow();

            assertThat(series.volumes())
                .filteredOn(volume -> volume.position() == SECOND_POSITION)
                .extracting(volume -> volume.book().title())
                .containsExactly(GREAT_HUNT);
        }

        @Test
        @DisplayName("a position with only a non-English edition is dropped")
        void dropsPositionWithoutEnglishEdition() {
            stubSearchAndEnum();

            final SourceSeries series = client.fetchSeries(QUERY).orElseThrow();

            assertThat(series.volumes())
                .filteredOn(volume -> volume.position() == THIRD_POSITION)
                .isEmpty();
        }

        @Test
        @DisplayName("position 0 prequels and positions past the primary count are dropped")
        void dropsPrequelAndPositionsPastPrimaryCount() {
            stubSearchAndEnum();

            final SourceSeries series = client.fetchSeries(QUERY).orElseThrow();

            assertThat(series.volumes())
                .extracting(SourceSeriesVolume::position, volume -> volume.book().title())
                .containsExactly(
                    tuple(FIRST_POSITION, EYE),
                    tuple(SECOND_POSITION, GREAT_HUNT));
        }

        @Test
        @DisplayName("at one position the audiobook, deluxe edition, and boxed set lose to the plain book")
        void rejectsVariantEditionsAndPicksPlainBook() {
            stubSearchAndEnum();

            final SourceSeries series = client.fetchSeries(QUERY).orElseThrow();

            assertThat(series.volumes())
                .filteredOn(volume -> volume.position() == FIRST_POSITION)
                .extracting(volume -> volume.book().title())
                .as("the audiobook and boxed set are dropped, the deluxe edition is rejected despite "
                    + "the most reads, so the plain print wins")
                .containsExactly(EYE);
        }

        @Test
        @DisplayName("a single graphic-novel issue is dropped for the collected volume")
        void dropsSingleComicIssueForCollectedVolume() {
            stub(GN_SEARCH_JSON, GN_ENUM_JSON);

            final SourceSeries series = client.fetchSeries(GN_QUERY).orElseThrow();

            assertThat(series.volumes())
                .extracting(volume -> volume.book().title())
                .containsExactly("The Sandman, Vol. 1: Preludes & Nocturnes");
        }

        @Test
        @DisplayName("each volume's book carries the series name and its position")
        void volumeBookCarriesSeriesNameAndPosition() {
            stubSearchAndEnum();

            final SourceSeries series = client.fetchSeries(QUERY).orElseThrow();

            assertThat(series.volumes())
                .first()
                .satisfies(volume -> {
                    assertThat(volume.book().seriesName()).isEqualTo(SERIES_NAME);
                    assertThat(volume.book().seriesPosition()).isEqualTo(FIRST_POSITION);
                });
        }
    }

    @Nested
    @DisplayName("error handling")
    class ErrorHandling {

        @Test
        @DisplayName("a 401 from a rejected token resolves to empty")
        void unauthorizedIsEmpty() {
            WIREMOCK.stubFor(post(urlPathEqualTo(GRAPHQL_PATH))
                .willReturn(aResponse().withStatus(HTTP_UNAUTHORIZED)));

            assertThat(client.fetchSeries(QUERY)).isEmpty();
        }

        @Test
        @DisplayName("a 5xx propagates so an outage is not read as 'series not found'")
        void serverErrorPropagates() {
            WIREMOCK.stubFor(post(urlPathEqualTo(GRAPHQL_PATH))
                .willReturn(aResponse().withStatus(HTTP_SERVER_ERROR)));

            assertThatThrownBy(() -> client.fetchSeries(QUERY))
                .isInstanceOf(WebClientResponseException.class);
        }
    }
}
