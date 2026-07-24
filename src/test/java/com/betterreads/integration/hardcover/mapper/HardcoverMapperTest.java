package com.betterreads.integration.hardcover.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.stream.Stream;

import com.betterreads.catalog.service.source.model.SourceBook;
import com.betterreads.integration.hardcover.dto.HardcoverDocument;
import com.betterreads.integration.hardcover.dto.HardcoverDocument.FeaturedSeries;
import com.betterreads.integration.hardcover.dto.HardcoverDocument.Image;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Maps a Hardcover search document onto a {@link SourceBook}.
 *
 * <p>The Dune cases target shapes the live API returned on 2026-05-31: the bulk ISBN-10/ISBN-13
 * array, the noisy genre list, the whole series position, and the rating and vote count.
 */
class HardcoverMapperTest {

    private static final String DUNE_ISBN_13 = "9780792748663";

    private static final String SCIENCE_FICTION = "science fiction";

    private static final String FICTION = "fiction";

    private static final double DUNE_RATING = 4.315_726_179_463_46;

    private static final double RATING_TOLERANCE = 1e-9;

    private static final int DUNE_RATING_COUNT = 5405;

    private static final String ISBN_10 = "0792748662";

    private static final String ISBN_OTHER = "8385432167";

    private static final String GENRE_SCIENCE_FICTION_RAW = "Science Fiction";

    private static final String GENRE_FICTION_RAW = "Fiction";

    private static final String DUNE_TITLE = "Dune";

    private static final String SUN_EATER_SERIES = "The Sun Eater";

    private static final String DUNE_HARDCOVER_ID = "312460";

    private static final String DUNE_COVER_URL = "https://assets.hardcover.app/dune.jpg";

    private static final double FRACTIONAL_POSITION = 0.5;

    @Nested
    @DisplayName("firstIsbn13")
    class FirstIsbn13 {

        @Test
        @DisplayName("picks the first ISBN-13 out of the bulk ISBN-10/13 mix, not the first entry")
        void picksIsbn13FromMixedArray() {
            final List<String> isbns = List.of(ISBN_10, DUNE_ISBN_13, ISBN_OTHER);

            assertThat(HardcoverMapper.firstIsbn13(isbns))
                .as("isbns interleaves 10 and 13; indexing entry 0 would store an ISBN-10")
                .isEqualTo(DUNE_ISBN_13);
        }

        @Test
        @DisplayName("an array with no ISBN-13 yields null, never a coerced ISBN-10")
        void noIsbn13YieldsNull() {
            assertThat(HardcoverMapper.firstIsbn13(List.of(ISBN_10, ISBN_OTHER))).isNull();
        }
    }

    @Nested
    @DisplayName("seriesPosition")
    class SeriesPosition {

        static Stream<Arguments> positions() {
            return Stream.of(
                Arguments.of(2.0, 2),
                Arguments.of(FRACTIONAL_POSITION, null),
                Arguments.of(0.0, null));
        }

        @ParameterizedTest(name = "position {0} maps to volume {1}")
        @MethodSource("positions")
        @DisplayName("only a whole position of one or more is a volume the catalog stores")
        void mapsWholePositionsFromOneUp(final double position, final Integer volume) {
            assertThat(HardcoverMapper.seriesPosition(position))
                .as("a fractional position is a prologue, position 0 is a prequel")
                .isEqualTo(volume);
        }
    }

    @Nested
    @DisplayName("toSourceBook")
    class ToSourceBook {

        private final HardcoverMapper mapper = new HardcoverMapper();

        @Test
        @DisplayName("maps rating, vote count, and series from the document onto the SourceBook")
        void mapsReaderSignal() {
            final HardcoverDocument dune = new HardcoverDocument(
                DUNE_HARDCOVER_ID, DUNE_TITLE, "Set on Arrakis.", 1965, 704,
                DUNE_RATING, DUNE_RATING_COUNT, 7405,
                List.of("Frank Herbert"), List.of(ISBN_10, DUNE_ISBN_13),
                List.of(GENRE_SCIENCE_FICTION_RAW, GENRE_FICTION_RAW),
                new Image(DUNE_COVER_URL),
                new FeaturedSeries(1.0, new FeaturedSeries.Series(DUNE_TITLE)));

            assertThat(mapper.toSourceBook(dune)).satisfies(book -> {
                assertThat(book.hardcoverId()).isEqualTo(DUNE_HARDCOVER_ID);
                assertThat(book.averageRating())
                    .as("the rating passes through as the raw Double, not rounded")
                    .isCloseTo(DUNE_RATING, within(RATING_TOLERANCE));
                assertThat(book.ratingCount()).isEqualTo(DUNE_RATING_COUNT);
                assertThat(book.seriesName()).isEqualTo(DUNE_TITLE);
                assertThat(book.seriesPosition()).isEqualTo(1);
                assertThat(book.coverUrl()).isEqualTo(DUNE_COVER_URL);
                assertThat(book.rawSubjects())
                    .as("raw Hardcover genres land in rawSubjects as canonical shelf terms")
                    .contains(SCIENCE_FICTION, FICTION);
            });
        }

        @Test
        @DisplayName("a null-position featured series leaves the book unlabelled, not volume null")
        void nullSeriesPositionCarriesNoSeries() {
            final HardcoverDocument companion = new HardcoverDocument(
                DUNE_HARDCOVER_ID, "The World of The Sun Eater", null, 2020, null, null, null, null,
                null, null, null, null,
                new FeaturedSeries(null, new FeaturedSeries.Series(SUN_EATER_SERIES)));

            assertThat(mapper.toSourceBook(companion)).satisfies(book -> {
                assertThat(book.seriesName()).isNull();
                assertThat(book.seriesPosition()).isNull();
            });
        }

        @Test
        @DisplayName("a document with no title is unmappable and returns null")
        void noTitleYieldsNull() {
            final HardcoverDocument titleless = new HardcoverDocument(
                "999", null, null, null, null, 5.0, 1, 1, null, null, null, null, null);

            assertThat(mapper.toSourceBook(titleless))
                .as("a stub hit with a rating but no title cannot become a catalog book")
                .isNull();
        }

        @Test
        @DisplayName("a document with no genres yields null subjects so a refresh keeps existing rows")
        void absentGenresYieldNullSubjects() {
            final HardcoverDocument noGenres = new HardcoverDocument(
                DUNE_HARDCOVER_ID, DUNE_TITLE, null, null, null, null, null, null, null, null, null,
                null, null);

            assertThat(mapper.toSourceBook(noGenres))
                .isNotNull()
                .extracting(SourceBook::rawSubjects)
                .as("empty would clear book_subject rows; null leaves another source's subjects intact")
                .isNull();
        }
    }
}
