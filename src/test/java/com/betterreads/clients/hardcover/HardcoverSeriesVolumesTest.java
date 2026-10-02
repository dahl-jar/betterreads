package com.betterreads.clients.hardcover;

import static com.betterreads.clients.hardcover.BookByIdJson.COSMERE;
import static com.betterreads.clients.hardcover.BookByIdJson.COSMERE_VOLUME;
import static com.betterreads.clients.hardcover.BookByIdJson.STORMLIGHT;
import static com.betterreads.clients.hardcover.BookByIdJson.STORMLIGHT_VOLUME;
import static com.betterreads.clients.hardcover.BookByIdJson.bookById;
import static com.betterreads.clients.hardcover.BookByIdJson.wordsOfRadiance;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.SeriesEntry;
import com.betterreads.booksource.SourceBook;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class HardcoverSeriesVolumesTest {

    private static final String RED_RISING = "Red Rising";

    private static final String RED_RISING_SAGA = "Red Rising Saga";

    private static final int TRILOGY_BOOKS = 3;

    private static final int SAGA_BOOKS = 7;

    private static final String IRON_GOLD = "Iron Gold";

    private static final int IRON_GOLD_VOLUME = 4;

    private static final int ARC_BOOKS = 4;

    private static final int UMBRELLA_BOOKS = 40;

    private static List<SeriesEntry> seriesOf(final HardcoverBookNode node) {
        final SourceBook book =
            HardcoverSeriesVolumes.withSeriesOf(SourceBook.builder(BookFieldSource.HARDCOVER), node).build();
        return book.series();
    }

    @Test
    void shouldListALargerSeriesAfterThePrimary() {
        final HardcoverBookNode node = wordsOfRadiance().node();

        final List<SeriesEntry> series = seriesOf(node);

        assertThat(series).containsExactly(
            new SeriesEntry(STORMLIGHT, STORMLIGHT_VOLUME), new SeriesEntry(COSMERE, COSMERE_VOLUME));
    }

    @Test
    void shouldPickTheLongerFeaturedSeries() {
        final HardcoverBookNode node = bookById().withTitle(RED_RISING).withoutSeries()
            .withSeries(RED_RISING, 1, true, TRILOGY_BOOKS)
            .withSeries(RED_RISING_SAGA, 1, true, SAGA_BOOKS)
            .node();

        final List<SeriesEntry> series = seriesOf(node);

        assertThat(series).first().isEqualTo(new SeriesEntry(RED_RISING_SAGA, 1));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {ARC_BOOKS, SAGA_BOOKS})
    void shouldLeaveOutASeriesNoLargerThanThePrimary(final @Nullable Integer arcBooks) {
        final HardcoverBookNode node = bookById().withTitle(IRON_GOLD).withoutSeries()
            .withSeries(RED_RISING_SAGA, IRON_GOLD_VOLUME, true, SAGA_BOOKS)
            .withSeries(IRON_GOLD, 1, true, arcBooks)
            .node();

        final List<SeriesEntry> series = seriesOf(node);

        assertThat(series).containsExactly(new SeriesEntry(RED_RISING_SAGA, IRON_GOLD_VOLUME));
    }

    @ParameterizedTest
    @CsvSource({
        "'The Stormlight Archive: Words of Radiance', 1",
        "'Cosmere Companions', "
    })
    void shouldSkipMembershipsThatAreNotVolumes(final String name, final @Nullable Integer position) {
        final HardcoverBookNode node = wordsOfRadiance().withSeries(name, position, false, UMBRELLA_BOOKS).node();

        final List<SeriesEntry> series = seriesOf(node);

        assertThat(series).extracting(SeriesEntry::name).containsExactly(STORMLIGHT, COSMERE);
    }
}
