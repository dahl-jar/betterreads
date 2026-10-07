package com.betterreads.clients.hardcoverseries;

import com.betterreads.booksource.SeriesEntry;
import com.betterreads.booksource.SourceSeries;
import com.betterreads.booksource.SourceSeriesVolume;
import com.betterreads.clients.hardcover.HardcoverProperties;
import com.betterreads.clients.hardcover.HardcoverWebClientConfig;
import com.betterreads.clients.hardcover.HardcoverWireMock;
import com.betterreads.clients.hardcover.SeriesBooksJson;
import com.betterreads.clients.hardcover.SeriesSearchJson;
import static com.betterreads.clients.hardcover.BookByIdJson.COSMERE;
import static com.betterreads.clients.hardcover.BookByIdJson.COSMERE_BOOKS;
import static com.betterreads.clients.hardcover.BookByIdJson.COSMERE_VOLUME;
import static com.betterreads.clients.hardcover.BookByIdJson.STORMLIGHT;
import static com.betterreads.clients.hardcover.BookByIdJson.STORMLIGHT_BOOKS;
import static com.betterreads.clients.hardcover.BookByIdJson.STORMLIGHT_VOLUME;
import static com.betterreads.clients.hardcover.SeriesBooksJson.seriesBooks;
import static com.betterreads.clients.hardcover.SeriesSearchJson.seriesSearch;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.InstanceOfAssertFactories.list;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.assertj.core.api.ListAssert;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

    private static final double FIRST_POSITION = 1;

    private static final double SECOND_POSITION = 2;

    private static final double THIRD_POSITION = 3;

    private static final double PREQUEL_POSITION = 0;

    private static final double NEGATIVE_POSITION = -1;

    private static final String NEGATIVE_TITLE = "The World of Robert Jordan's The Wheel of Time";

    private static final String NOVELLA_PLOT = "The age of legends before the breaking.";

    private static final String GREAT_HUNT = "The Great Hunt";

    private static final String GREAT_HUNT_PLOT = "Rand rides after the Horn of Valere.";

    private static final String SERIES_NAME = "The Wheel of Time";

    private static final String PARODY_NAME = "Wheel of Time Parody";

    private static final String GRAPHIC_NOVEL_QUERY = "the sandman";

    private static final String COSMERE_QUERY = "cosmere";

    private static final String PARODY_QUERY = "wheel of time";

    private static final String UNRELATED_NAME = "Truth Matters";

    @Autowired
    private HardcoverSeriesClientImpl client;

    private void stubSearchAndBooks() {
        stub(seriesSearch(), seriesBooks());
    }

    private static SeriesSearchJson unrelatedSearch() {
        return seriesSearch().withSeries(UNRELATED_NAME, "Phil Johnson");
    }

    private void stub(final SeriesSearchJson search, final SeriesBooksJson books) {
        stubGraphQl(SEARCH_MARKER, search);
        stubGraphQl(BOOKS_MARKER, books);
    }

    private static List<Tuple> volumes(final Optional<SourceSeries> series) {
        return series.orElseThrow().volumes().stream()
            .map(volume -> tuple(volume.position(), volume.book().title()))
            .toList();
    }

    private static List<String> titles(final Optional<SourceSeries> series) {
        return series.orElseThrow().volumes().stream().map(volume -> volume.book().title()).toList();
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

            final Optional<SourceSeries> series = client.fetchSeries(PARODY_QUERY);

            assertThat(series).get().extracting(SourceSeries::name).isEqualTo(PARODY_NAME);
        }

        @Test
        void shouldSkipOneBookHit() {
            stub(seriesSearch().withPrimaryBooksCount(1), seriesBooks());

            final Optional<SourceSeries> series = client.fetchSeries(PARODY_QUERY);

            assertThat(series).get().extracting(SourceSeries::name).isEqualTo(PARODY_NAME);
        }

        @Test
        void shouldKeepHitWithUnknownBookCount() {
            stub(seriesSearch().withoutPrimaryBooksCount(), seriesBooks());

            final Optional<SourceSeries> series = client.fetchSeries(QUERY);

            assertThat(series).get().extracting(SourceSeries::name).isEqualTo(SERIES_NAME);
        }

        @Test
        void shouldSkipASeriesWhoseNameIsNotInTheQuery() {
            stub(unrelatedSearch(), seriesBooks());

            final Optional<SourceSeries> series = client.fetchSeries(QUERY);

            assertThat(series).isEmpty();
        }

        @Test
        void shouldFindASeriesNamedInALongerQuery() {
            stubSearchAndBooks();

            final Optional<SourceSeries> series = client.fetchSeries("the wheel of time robert jordan");

            assertThat(series).get().extracting(SourceSeries::name).isEqualTo(SERIES_NAME);
        }

        @Test
        void shouldPickAMatchingSeriesOverAMoreReadOne() {
            stub(unrelatedSearch(), seriesBooks());

            final Optional<SourceSeries> series = client.fetchSeries(PARODY_NAME);

            assertThat(series).get().extracting(SourceSeries::name).isEqualTo(PARODY_NAME);
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
        void shouldKeepThePrequelAtPositionZero() {
            stubSearchAndBooks();

            final Optional<SourceSeries> series = client.fetchSeries(QUERY);

            assertThat(volumes(series))
                .contains(tuple(PREQUEL_POSITION, "New Spring"));
        }

        @Test
        void shouldDropVolumesPastThePrimaryBookCount() {
            stubSearchAndBooks();

            final Optional<SourceSeries> series = client.fetchSeries(QUERY);

            assertThat(titles(series))
                .doesNotContain("A Crown of Swords");
        }

        @ParameterizedTest
        @CsvSource({"0.5, Origins", "1.5, The Strike at Shayol Ghul"})
        void shouldKeepAFractionalVolume(final double position, final String novella) {
            stub(seriesSearch(), seriesBooks().withoutVolumes()
                .withVolume(THIRD_POSITION, GREAT_HUNT, GREAT_HUNT_PLOT)
                .withVolume(position, novella, NOVELLA_PLOT));

            final Optional<SourceSeries> series = client.fetchSeries(QUERY);

            assertThat(volumes(series))
                .containsExactly(tuple(position, novella), tuple(THIRD_POSITION, GREAT_HUNT));
        }

        @Test
        void shouldLeaveOutANegativeVolume() {
            stub(seriesSearch(), seriesBooks().withoutVolumes()
                .withVolume(THIRD_POSITION, GREAT_HUNT, GREAT_HUNT_PLOT)
                .withVolume(NEGATIVE_POSITION, NEGATIVE_TITLE, NOVELLA_PLOT));

            final Optional<SourceSeries> series = client.fetchSeries(QUERY);

            assertThat(titles(series))
                .containsExactly(GREAT_HUNT);
        }

        @Test
        void shouldKeepTheEnglishSingleBookAtEachPosition() {
            stubSearchAndBooks();

            final Optional<SourceSeries> series = client.fetchSeries(QUERY);

            assertThat(volumes(series))
                .contains(
                    tuple(FIRST_POSITION, "The Eye of the World"),
                    tuple(SECOND_POSITION, GREAT_HUNT));
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

            assertThat(titles(series))
                .containsExactly(collected);
        }

        @Test
        void shouldKeepEachVolumesOwnPrimarySeries() {
            stub(cosmereSearch(), cosmereVolume()
                .withMembership(COSMERE, COSMERE_VOLUME, false, COSMERE_BOOKS)
                .withMembership(STORMLIGHT, STORMLIGHT_VOLUME, true, STORMLIGHT_BOOKS));

            final Optional<SourceSeries> series = client.fetchSeries(COSMERE_QUERY);

            assertThatSeriesOfTheOnlyVolume(series).containsExactly(new SeriesEntry(STORMLIGHT, STORMLIGHT_VOLUME));
        }

        @Test
        void shouldLeaveTheSeriesEmptyWhenTheVolumeHasNoNumberedMembership() {
            stub(cosmereSearch(), cosmereVolume().withMembership(COSMERE, 0, true, COSMERE_BOOKS));

            final Optional<SourceSeries> series = client.fetchSeries(COSMERE_QUERY);

            assertThatSeriesOfTheOnlyVolume(series).isEmpty();
        }

        private static ListAssert<SeriesEntry> assertThatSeriesOfTheOnlyVolume(final Optional<SourceSeries> series) {
            return assertThat(series).get()
                .extracting(SourceSeries::volumes, list(SourceSeriesVolume.class))
                .singleElement()
                .extracting(volume -> volume.book().series(), list(SeriesEntry.class));
        }

        private static SeriesSearchJson cosmereSearch() {
            return seriesSearch().withSeries(COSMERE, "Brandon Sanderson");
        }

        private static SeriesBooksJson cosmereVolume() {
            return seriesBooks().withName(COSMERE).withBookCount(COSMERE_VOLUME).withoutVolumes()
                .withVolume(COSMERE_VOLUME, "Words of Radiance", "Shallan joins Kaladin on the Shattered Plains.");
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
