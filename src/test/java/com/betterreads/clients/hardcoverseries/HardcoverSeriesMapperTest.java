package com.betterreads.clients.hardcoverseries;

import static com.betterreads.clients.hardcover.SeriesBooksJson.seriesBooks;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import java.util.Optional;

import com.betterreads.booksource.SeriesEntry;
import com.betterreads.booksource.SourceBook;
import com.betterreads.booksource.SourceSeries;
import com.betterreads.clients.hardcover.SeriesBookJson;
import com.betterreads.clients.hardcover.SeriesBooksJson;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HardcoverSeriesMapperTest {

    private static final String LOTR = "The Lord of the Rings";

    private static final String TOLKIEN = "J.R.R. Tolkien";

    private static final String FELLOWSHIP = "The Fellowship of the Ring";

    private static final String TOWERS = "The Two Towers";

    private static final String KING = "The Return of the King";

    private static final String DUNE = "Dune";

    private static final String GERMAN = "German";

    private static final long LOTR_ID = 377_938L;

    private static final long LESSER_LOTR_ID = 377_939L;

    private static final long GERMAN_LOTR_ID = 377_940L;

    private static final long FELLOWSHIP_ID = 139_773L;

    private static final long TOWERS_ID = 2L;

    private static final long KING_ID = 3L;

    private static final long DUNE_ID = 312_460L;

    private static final long DUNE_EDITION_ID = 312_461L;

    private static final long MESSIAH_ID = 5L;

    private static final long OMNIBUS_ID = 9L;

    private static final int LOTR_READERS = 4_829;

    private static final int LESSER_LOTR_READERS = 2_500;

    private static final int GERMAN_LOTR_READERS = 6_000;

    private static final int FELLOWSHIP_READERS = 10_028;

    private static final int TOWERS_READERS = 8_000;

    private static final int KING_READERS = 7_500;

    private static final int DUNE_READERS = 20_000;

    private static final int DUNE_EDITION_READERS = 9_000;

    private static final int MESSIAH_READERS = 9_000;

    private static final int OMNIBUS_READERS = 12_000;

    private static final int TOP_READERS = 10_030;

    private static final int FIFTH_OF_TOP_READERS = 2_006;

    private static final int BELOW_A_FIFTH_READERS = 2_005;

    private static final int PRIMARY_BOOKS = 3;

    private static final double FIRST = 1;

    private static final double SECOND = 2;

    private static final double THIRD = 3;

    private final HardcoverSeriesMapper mapper = new HardcoverSeriesMapper();

    @Test
    void shouldGiveTheTitleBookNoSeriesEntry() {
        final SeriesBooksJson rows = trilogy(FELLOWSHIP_READERS)
            .with(row(FIRST, LOTR_ID, LOTR, LOTR_READERS).compilation())
            .withMembership(LOTR, 1, true, PRIMARY_BOOKS);

        final SourceSeries series = map(LOTR, rows);

        assertThat(series).extracting(SourceSeries::titleBook)
            .extracting(SourceBook::series, InstanceOfAssertFactories.list(SeriesEntry.class))
            .isEmpty();
    }

    @Test
    void shouldPickTheMostReadTitleBook() {
        final SeriesBooksJson rows = trilogy(FELLOWSHIP_READERS)
            .with(row(FIRST, LESSER_LOTR_ID, LOTR, LESSER_LOTR_READERS).compilation())
            .with(row(FIRST, LOTR_ID, LOTR, LOTR_READERS).compilation());

        final SourceSeries series = map(LOTR, rows);

        assertThat(series).extracting(SourceSeries::titleBook)
            .extracting(SourceBook::hardcoverId)
            .isEqualTo(String.valueOf(LOTR_ID));
    }

    @Test
    void shouldSkipAMoreReadTitleBookThatDoesNotQualify() {
        final SeriesBooksJson rows = trilogy(FELLOWSHIP_READERS)
            .with(row(FIRST, GERMAN_LOTR_ID, LOTR, GERMAN_LOTR_READERS).compilation().inLanguage(GERMAN))
            .with(row(FIRST, LOTR_ID, LOTR, LOTR_READERS).compilation());

        final SourceSeries series = map(LOTR, rows);

        assertThat(series).extracting(SourceSeries::titleBook)
            .extracting(SourceBook::hardcoverId)
            .isEqualTo(String.valueOf(LOTR_ID));
    }

    @ParameterizedTest
    @CsvSource(value = {FIFTH_OF_TOP_READERS + ", " + LOTR_ID, BELOW_A_FIFTH_READERS + ", none"}, nullValues = "none")
    void shouldPickATitleBookOnlyFromAFifthOfTheTopReaders(final int readers, final @Nullable String picked) {
        final SeriesBooksJson rows = trilogy(TOP_READERS).with(row(FIRST, LOTR_ID, LOTR, readers).compilation());

        final SourceBook titleBook = Objects.requireNonNull(map(LOTR, rows)).titleBook();

        assertThat(Optional.ofNullable(titleBook).map(SourceBook::hardcoverId)).isEqualTo(Optional.ofNullable(picked));
    }

    @Test
    void shouldSkipATitleBookWhenNoVolumeHasReaders() {
        final SeriesBooksJson rows = seriesBooks().withoutVolumes().withBookCount(PRIMARY_BOOKS)
            .with(row(FIRST, FELLOWSHIP_ID, FELLOWSHIP, 0))
            .with(row(SECOND, TOWERS_ID, TOWERS, 0))
            .with(row(THIRD, KING_ID, KING, 0))
            .with(row(FIRST, LOTR_ID, LOTR, LOTR_READERS).compilation());

        final SourceSeries series = map(LOTR, rows);

        assertThat(series).extracting(SourceSeries::titleBook).isNull();
    }

    @Test
    void shouldSkipATitleBookThatIsAlreadyAVolume() {
        final SeriesBooksJson rows = seriesBooks().withoutVolumes()
            .with(row(FIRST, DUNE_ID, DUNE, DUNE_READERS))
            .with(row(FIRST, DUNE_EDITION_ID, DUNE, DUNE_EDITION_READERS))
            .with(row(SECOND, MESSIAH_ID, "Dune Messiah", MESSIAH_READERS));

        final SourceSeries series = map(DUNE, rows);

        assertThat(series).extracting(SourceSeries::titleBook).isNull();
    }

    @Test
    void shouldSkipAWellReadBookWithAnotherTitle() {
        final SeriesBooksJson rows = trilogy(FELLOWSHIP_READERS)
            .with(row(FIRST, OMNIBUS_ID, "The Hobbit and The Lord of the Rings", OMNIBUS_READERS).compilation());

        final SourceSeries series = map(LOTR, rows);

        assertThat(series).extracting(SourceSeries::titleBook).isNull();
    }

    @Test
    void shouldSkipANonEnglishTitleBook() {
        final SeriesBooksJson rows = trilogy(FELLOWSHIP_READERS)
            .with(row(FIRST, LOTR_ID, LOTR, LOTR_READERS).compilation().inLanguage(GERMAN));

        final SourceSeries series = map(LOTR, rows);

        assertThat(series).extracting(SourceSeries::titleBook).isNull();
    }

    private static SeriesBookJson row(final double position, final long id, final String title, final int readers) {
        return SeriesBookJson.book(position, title).id(id).readers(readers);
    }

    private static SeriesBooksJson trilogy(final int fellowshipReaders) {
        return seriesBooks().withoutVolumes().withBookCount(PRIMARY_BOOKS)
            .with(row(FIRST, FELLOWSHIP_ID, FELLOWSHIP, fellowshipReaders))
            .with(row(SECOND, TOWERS_ID, TOWERS, TOWERS_READERS))
            .with(row(THIRD, KING_ID, KING, KING_READERS));
    }

    private @Nullable SourceSeries map(final String name, final SeriesBooksJson rows) {
        final SeriesEnumerationResponse response = rows.as(SeriesEnumerationResponse.class);
        final SeriesEnumerationResponse.Data data = Objects.requireNonNull(response.data());
        final SeriesEnumerationResponse.Series enumerated = Objects.requireNonNull(data.series()).getFirst();
        return mapper.toSourceSeries(new SeriesSearchDocument("1", name, TOLKIEN, null, null), enumerated);
    }
}
