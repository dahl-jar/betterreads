package com.betterreads.clients.hardcover;

import static com.betterreads.clients.hardcover.BookByIdJson.COSMERE;
import static com.betterreads.clients.hardcover.BookByIdJson.COSMERE_BOOKS;
import static com.betterreads.clients.hardcover.BookByIdJson.COSMERE_VOLUME;
import static com.betterreads.clients.hardcover.BookByIdJson.LAST_KING;
import static com.betterreads.clients.hardcover.BookByIdJson.LAST_KING_BOOKS;
import static com.betterreads.clients.hardcover.BookByIdJson.LETZTE_KOENIG;
import static com.betterreads.clients.hardcover.BookByIdJson.STORMLIGHT;
import static com.betterreads.clients.hardcover.BookByIdJson.STORMLIGHT_BOOKS;
import static com.betterreads.clients.hardcover.BookByIdJson.STORMLIGHT_VOLUME;
import static com.betterreads.clients.hardcover.BookByIdJson.bookById;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.SeriesEntry;
import com.betterreads.booksource.SourceBook;
import org.junit.jupiter.api.Test;

class HardcoverSeriesVolumesTest {

    private static final String FANGIRL = "Fangirl";

    private static final int FANGIRL_BOOKS = 3;

    private static final int MANGA_BOOKS = 4;

    private static final double NOVELLA_POSITION = 2.5;

    private static final int NOVELLA_UMBRELLA_VOLUME = 19;

    private static final int READING_ORDER_VOLUME = 20;

    private static final int READING_ORDER_BOOKS = 40;

    private static List<SeriesEntry> seriesOf(final HardcoverBookNode node) {
        final SourceBook book =
            HardcoverSeriesVolumes.withSeriesOf(SourceBook.builder(BookFieldSource.HARDCOVER), node).build();
        return book.series();
    }

    @Test
    void shouldTakeHardcoversPickAsThePrimary() {
        final HardcoverBookNode node = bookById().withTitle("Fangirl, Vol. 2: The Manga").withoutSeries()
            .withSeries("Fangirl: The Manga", 2, true, MANGA_BOOKS)
            .withSeries(FANGIRL, 2, false, FANGIRL_BOOKS)
            .withFeaturedSeries(FANGIRL, 2, false, FANGIRL_BOOKS)
            .node();

        final List<SeriesEntry> series = seriesOf(node);

        assertThat(series).containsExactly(new SeriesEntry(FANGIRL, 2));
    }

    @Test
    void shouldTakeTheFirstFeaturedSeriesWithoutAPick() {
        final HardcoverBookNode node = bookById().withTitle("Words of Radiance").withoutSeries()
            .withSeries(COSMERE, COSMERE_VOLUME, false, COSMERE_BOOKS)
            .withSeries(STORMLIGHT, STORMLIGHT_VOLUME, true, STORMLIGHT_BOOKS)
            .withSeries("The Cosmere (Reading Order)", READING_ORDER_VOLUME, true, READING_ORDER_BOOKS)
            .node();

        final List<SeriesEntry> series = seriesOf(node);

        assertThat(series).containsExactly(new SeriesEntry(STORMLIGHT, STORMLIGHT_VOLUME));
    }

    @Test
    void shouldKeepAFractionalPosition() {
        final HardcoverBookNode node = bookById().withTitle("Edgedancer").withoutSeries()
            .withSeries(COSMERE, NOVELLA_UMBRELLA_VOLUME, false, COSMERE_BOOKS)
            .withSeries(STORMLIGHT, NOVELLA_POSITION, true, STORMLIGHT_BOOKS)
            .withFeaturedSeries(STORMLIGHT, NOVELLA_POSITION, true, STORMLIGHT_BOOKS)
            .node();

        final List<SeriesEntry> series = seriesOf(node);

        assertThat(series).containsExactly(new SeriesEntry(STORMLIGHT, NOVELLA_POSITION));
    }

    @Test
    void shouldSkipAPickThatIsAOneBookSeries() {
        final HardcoverBookNode node = bookById().withTitle("The Witchwood Crown").withoutSeries()
            .withSeries(LAST_KING, 1, false, LAST_KING_BOOKS)
            .withSeries(LETZTE_KOENIG, 1, true, 1)
            .withFeaturedSeries(LETZTE_KOENIG, 1, true, 1)
            .node();

        final List<SeriesEntry> series = seriesOf(node);

        assertThat(series).containsExactly(new SeriesEntry(LAST_KING, 1));
    }
}
