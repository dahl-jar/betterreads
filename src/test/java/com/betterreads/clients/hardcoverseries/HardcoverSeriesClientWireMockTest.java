package com.betterreads.clients.hardcoverseries;

import com.betterreads.booksource.SourceSeries;
import com.betterreads.booksource.SourceSeriesVolume;
import com.betterreads.clients.hardcover.HardcoverProperties;
import com.betterreads.clients.hardcover.HardcoverWebClientConfig;
import com.betterreads.clients.hardcover.HardcoverWireMock;
import com.betterreads.clients.hardcover.SeriesBooksJson;
import com.betterreads.clients.hardcover.SeriesSearchJson;
import static com.betterreads.clients.hardcover.SeriesBooksJson.seriesBooks;
import static com.betterreads.clients.hardcover.SeriesSearchJson.seriesSearch;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.InstanceOfAssertFactories.list;

import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

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

    private static final String PARODY_NAME = "Wheel of Time Parody";

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
        @DisplayName("picks the series with the most readers over search rank 0")
        void picksHighestReaderSeriesNotRankZero() {
            stubSearchAndBooks();

            final Optional<SourceSeries> series = client.fetchSeries(QUERY);

            assertThat(series).get().satisfies(value -> {
                assertThat(value.name()).isEqualTo(SERIES_NAME);
                assertThat(value.author()).isEqualTo("Robert Jordan");
            });
        }

        @Test
        @DisplayName("a zero-book container series is rejected")
        void zeroBookSeriesRejected() {
            stub(seriesSearch(), seriesBooks().withBookCount(0));

            final Optional<SourceSeries> series = client.fetchSeries(QUERY);

            assertThat(series)
                .as("should reject a zero-book container series")
                .isEmpty();
        }

        @Test
        void shouldSkipAHitWithoutAName() {
            stub(seriesSearch().withoutName(), seriesBooks());

            final Optional<SourceSeries> series = client.fetchSeries(QUERY);

            assertThat(series).get().extracting(SourceSeries::name).isEqualTo(PARODY_NAME);
        }

        @Test
        void shouldSkipOneBookHit() {
            stub(seriesSearch().withPrimaryBooksCount(1), seriesBooks());

            final Optional<SourceSeries> series = client.fetchSeries(QUERY);

            assertThat(series).get().extracting(SourceSeries::name).isEqualTo(PARODY_NAME);
        }

        @Test
        void shouldKeepHitWithUnknownBookCount() {
            stub(seriesSearch().withoutPrimaryBooksCount(), seriesBooks());

            final Optional<SourceSeries> series = client.fetchSeries(QUERY);

            assertThat(series).get().extracting(SourceSeries::name).isEqualTo(SERIES_NAME);
        }

        @Test
        void shouldResolveToEmptyWhenTheSeriesIdIsNotNumeric() {
            stub(seriesSearch().withId("not-a-number"), seriesBooks());

            final Optional<SourceSeries> series = client.fetchSeries(QUERY);

            assertThat(series).isEmpty();
        }

        @Test
        void shouldResolveToEmptyWhenTheVolumeQueryFindsNoSeries() {
            stubGraphQl(SEARCH_MARKER, seriesSearch());
            stubGraphQl(BOOKS_MARKER, "{\"data\": {\"series\": []}}");

            final Optional<SourceSeries> series = client.fetchSeries(QUERY);

            assertThat(series).isEmpty();
        }
    }

    @Nested
    @DisplayName("volume collapse")
    class VolumeCollapse {

        @Test
        void shouldDropThePrequelAtPositionZero() {
            assertVolumeDropped("New Spring");
        }

        @Test
        void shouldDropVolumesPastThePrimaryBookCount() {
            assertVolumeDropped("A Crown of Swords");
        }

        private void assertVolumeDropped(final String title) {
            stubSearchAndBooks();

            final Optional<SourceSeries> series = client.fetchSeries(QUERY);

            assertThat(series).get()
                .extracting(SourceSeries::volumes, list(SourceSeriesVolume.class))
                .extracting(volume -> volume.book().title())
                .doesNotContain(title);
        }

        @Test
        void shouldKeepTheEnglishSingleBookAtEachPosition() {
            stubSearchAndBooks();

            final Optional<SourceSeries> series = client.fetchSeries(QUERY);

            assertThat(series).get()
                .extracting(SourceSeries::volumes, list(SourceSeriesVolume.class))
                .extracting(SourceSeriesVolume::position, volume -> volume.book().title())
                .contains(
                    tuple(FIRST_POSITION, "The Eye of the World"),
                    tuple(SECOND_POSITION, "The Great Hunt"));
        }

        @Test
        @DisplayName("a single graphic-novel issue is dropped for the collected volume")
        void dropsSingleComicIssueForCollectedVolume() {
            final String sandman = "The Sandman";
            final String collected = "The Sandman, Vol. 1: Preludes & Nocturnes";
            stub(seriesSearch().withSeries(sandman, "Neil Gaiman"),
                seriesBooks().withName(sandman).withBookCount(1).withoutVolumes()
                    .withComicVolume("The Sandman #1: Sleep of the Just", false)
                    .withComicVolume(collected, true));

            final Optional<SourceSeries> series = client.fetchSeries(GRAPHIC_NOVEL_QUERY);

            assertThat(series).get()
                .extracting(SourceSeries::volumes, list(SourceSeriesVolume.class))
                .extracting(volume -> volume.book().title())
                .containsExactly(collected);
        }

        @Test
        @DisplayName("each volume's book carries the series name and its position")
        void volumeBookCarriesSeriesNameAndPosition() {
            stubSearchAndBooks();

            final Optional<SourceSeries> series = client.fetchSeries(QUERY);

            assertThat(series).get()
                .extracting(SourceSeries::volumes, list(SourceSeriesVolume.class))
                .first()
                .satisfies(volume -> {
                    assertThat(volume.book().seriesName()).isEqualTo(SERIES_NAME);
                    assertThat(volume.book().seriesPosition()).isEqualTo(FIRST_POSITION);
                });
        }
    }

    @Nested
    @DisplayName("large responses")
    class LargeResponses {

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

            final Optional<SourceSeries> series = client.fetchSeries(QUERY);

            assertThat(series).get().extracting(SourceSeries::name).isEqualTo(SERIES_NAME);
        }
    }
}
