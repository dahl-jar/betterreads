package com.betterreads.integration.hardcover.client;

import static com.betterreads.integration.hardcover.SeriesBooksJson.seriesBooks;
import static com.betterreads.integration.hardcover.SeriesSearchJson.seriesSearch;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.util.stream.IntStream;

import com.betterreads.catalog.service.source.model.SourceSeries;
import com.betterreads.catalog.service.source.model.SourceSeriesVolume;
import com.betterreads.integration.hardcover.HardcoverProperties;
import com.betterreads.integration.hardcover.HardcoverWebClientConfig;
import com.betterreads.integration.hardcover.HardcoverWireMock;
import com.betterreads.integration.hardcover.SeriesBooksJson;
import com.betterreads.integration.hardcover.SeriesSearchJson;
import com.betterreads.integration.hardcover.mapper.HardcoverSeriesMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@SpringBootTest(
    classes = {
        HardcoverWebClientConfig.class,
        HardcoverSeriesClientImpl.class,
        HardcoverSeriesMapper.class
    },
    properties = "spring.main.web-application-type=none"
)
@EnableConfigurationProperties(HardcoverProperties.class)
class HardcoverSeriesClientWireMockTest extends HardcoverWireMock {

    private static final int DEFAULT_DECODE_BUFFER_BYTES = 256 * 1024;

    private static final int LARGE_SERIES_VOLUME_COUNT = 400;

    private static final int VOLUME_PADDING_WORDS = 80;

    private static final String SEARCH_MARKER = "query_type: \\\"Series\\\"";

    private static final String BOOKS_MARKER = "book_series";

    private static final String QUERY = "the wheel of time";

    private static final int FIRST_POSITION = 1;

    private static final int SECOND_POSITION = 2;

    private static final String SERIES_NAME = "The Wheel of Time";

    private static final String EYE = "The Eye of the World";

    private static final String GREAT_HUNT = "The Great Hunt";

    private static final String GRAPHIC_NOVEL_QUERY = "the sandman";

    @Autowired
    private HardcoverSeriesClientImpl client;

    private void stubSearchAndBooks() {
        stub(seriesSearch(), seriesBooks());
    }

    private void stub(final SeriesSearchJson search, final SeriesBooksJson books) {
        stubGraphQl(SEARCH_MARKER, search);
        stubGraphQl(BOOKS_MARKER, books);
    }

    @Nested
    @DisplayName("candidate selection")
    class CandidateSelection {

        @Test
        @DisplayName("picks the series with the most readers, not search rank 0")
        void picksHighestReaderSeriesNotRankZero() {
            stubSearchAndBooks();

            final SourceSeries series = client.fetchSeries(QUERY).orElseThrow();

            assertThat(series.name()).isEqualTo(SERIES_NAME);
            assertThat(series.author()).isEqualTo("Robert Jordan");
        }

        @Test
        @DisplayName("no search hit resolves to empty")
        void noHitIsEmpty() {
            stubGraphQl(SEARCH_MARKER, seriesSearch().withoutHits());

            assertThat(client.fetchSeries(QUERY)).isEmpty();
        }

        @Test
        @DisplayName("a zero-book container series is rejected")
        void zeroBookSeriesRejected() {
            stub(seriesSearch().withBookCount(0), seriesBooks().withBookCount(0));

            assertThat(client.fetchSeries(QUERY))
                .as("should reject a zero-book container series")
                .isEmpty();
        }
    }

    @Nested
    @DisplayName("volume collapse")
    class VolumeCollapse {

        @Test
        @DisplayName("position 0 prequels and positions past the primary count are dropped")
        void dropsPrequelAndPositionsPastPrimaryCount() {
            stubSearchAndBooks();

            final SourceSeries series = client.fetchSeries(QUERY).orElseThrow();

            assertThat(series.volumes())
                .extracting(SourceSeriesVolume::position, volume -> volume.book().title())
                .containsExactly(
                    tuple(FIRST_POSITION, EYE),
                    tuple(SECOND_POSITION, GREAT_HUNT));
        }

        @Test
        @DisplayName("a single graphic-novel issue is dropped for the collected volume")
        void dropsSingleComicIssueForCollectedVolume() {
            final String sandman = "The Sandman";
            final String collected = "The Sandman, Vol. 1: Preludes & Nocturnes";
            stub(seriesSearch().withSeries(sandman, "Neil Gaiman").withBookCount(1),
                seriesBooks().withName(sandman).withBookCount(1).withoutVolumes()
                    .withComicVolume("The Sandman #1: Sleep of the Just", false)
                    .withComicVolume(collected, true));

            final SourceSeries series = client.fetchSeries(GRAPHIC_NOVEL_QUERY).orElseThrow();

            assertThat(series.volumes())
                .extracting(volume -> volume.book().title())
                .containsExactly(collected);
        }

        @Test
        @DisplayName("each volume's book carries the series name and its position")
        void volumeBookCarriesSeriesNameAndPosition() {
            stubSearchAndBooks();

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
            stubStatus(HTTP_UNAUTHORIZED);

            assertThat(client.fetchSeries(QUERY)).isEmpty();
        }

        @Test
        @DisplayName("a 5xx propagates so an outage is not read as 'series not found'")
        void serverErrorPropagates() {
            stubStatus(HTTP_SERVER_ERROR);

            assertThatThrownBy(() -> client.fetchSeries(QUERY))
                .isInstanceOf(WebClientResponseException.class);
        }

        @Test
        void shouldResolveASeriesBodyPastTheDefaultDecodeBuffer() {
            final SeriesBooksJson largeSeries = seriesBooks().withBookCount(LARGE_SERIES_VOLUME_COUNT).withoutVolumes();
            final String padding = "padding ".repeat(VOLUME_PADDING_WORDS);
            IntStream.rangeClosed(1, LARGE_SERIES_VOLUME_COUNT)
                .forEach(position -> largeSeries.withVolume(position, "Volume " + position, padding));
            assertThat(largeSeries.toString().length())
                .as("should build a body past the 256 KB default buffer")
                .isGreaterThan(DEFAULT_DECODE_BUFFER_BYTES);
            stub(seriesSearch(), largeSeries);

            final SourceSeries series = client.fetchSeries(QUERY).orElseThrow();

            assertThat(series.name()).isEqualTo(SERIES_NAME);
        }
    }
}
