package com.betterreads.integration.loc.mapper;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import com.betterreads.catalog.service.BookFieldSource;
import com.betterreads.catalog.service.SourceBook;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.ClassPathResource;

/**
 * Maps the captured MODS records onto a {@link SourceBook}.
 *
 * <p>The fixtures are real SRU responses for the trust slate, so the parse runs against the shapes
 * the live catalog returns rather than invented XML.
 */
class LocMapperTest {

    private static final String DUNE = "dune";
    private static final String WATCHMEN = "watchmen";
    private static final String HOBBIT = "hobbit";
    private static final String SANDMAN = "sandman";
    private static final String EYE = "eye-of-the-world";
    private static final String CLASH = "clash-of-kings";

    private static final int DUNE_YEAR = 2019;
    private static final int DUNE_PAGES = 658;
    private static final int WATCHMEN_YEAR = 2013;
    private static final int WATCHMEN_PAGES = 414;
    private static final int HOBBIT_YEAR = 2023;
    private static final int HOBBIT_PAGES = 272;
    private static final int SANDMAN_YEAR = 1991;
    private static final int EYE_PAGES = 670;
    private static final int CLASH_PAGES = 761;
    private static final int CLASH_POSITION = 2;

    private static final String WHEEL_OF_TIME = "Wheel of time";

    private final LocMapper mapper = new LocMapper();

    private record Expected(
        String slug,
        String lccn,
        String isbn13,
        Integer marcYear,
        Integer pageCount,
        String seriesName,
        Integer seriesPosition
    ) { }

    private static String fixture(final String slug) {
        try {
            return new ClassPathResource("integration/loc/" + slug + "-mods.xml")
                .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private SourceBook map(final String slug) {
        return mapper.toSourceBook(fixture(slug)).orElseThrow();
    }

    @Nested
    @DisplayName("toSourceBook")
    class ToSourceBook {

        static Stream<Expected> slate() {
            return Stream.of(
                new Expected(DUNE, "2019287107", "9780593099322", DUNE_YEAR, DUNE_PAGES,
                    "Dune chronicles", 1),
                new Expected(WATCHMEN, "2013003992", "9781401238964", WATCHMEN_YEAR, WATCHMEN_PAGES,
                    null, null),
                new Expected(HOBBIT, "2024442463", "9780063347533", HOBBIT_YEAR, HOBBIT_PAGES,
                    null, null),
                new Expected(SANDMAN, "92159876", "9781563890116", SANDMAN_YEAR, null,
                    null, null),
                new Expected(EYE, "89007939", "9780312850098", null, EYE_PAGES,
                    WHEEL_OF_TIME, 1),
                new Expected(CLASH, "98037954", "9780553108033", null, CLASH_PAGES,
                    "Song of ice and fire", CLASH_POSITION));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("slate")
        @DisplayName("each record yields its lccn, isbn-13, marc year, page count, and series")
        void parsesEachRecord(final Expected expected) {
            final SourceBook book = map(expected.slug());

            assertThat(book.source()).isEqualTo(BookFieldSource.LOC);
            assertThat(book.locLccn()).isEqualTo(expected.lccn());
            assertThat(book.isbn13())
                .as("the 978 isbn is picked from the interleaved identifiers")
                .isEqualTo(expected.isbn13());
            assertThat(book.publicationYear())
                .as("the marc-encoded year, or null when the record has none")
                .isEqualTo(expected.marcYear());
            assertThat(book.pageCount())
                .as("the page count from the extent, or null for a multi-volume extent")
                .isEqualTo(expected.pageCount());
            assertThat(book.seriesName()).isEqualTo(expected.seriesName());
            assertThat(book.seriesPosition()).isEqualTo(expected.seriesPosition());
        }

        static Stream<Arguments> authors() {
            return Stream.of(
                Arguments.of(DUNE, "Herbert, Frank"),
                Arguments.of(SANDMAN, "Gaiman, Neil"),
                Arguments.of(CLASH, "Martin, George R. R."),
                Arguments.of(HOBBIT, "Tolkien, J. R. R. (John Ronald Reuel)"));
        }

        @ParameterizedTest(name = "{0} -> {1}")
        @MethodSource("authors")
        @DisplayName("strips a trailing comma or period from the primary author, keeps an initial")
        void normalizesPrimaryAuthorName(final String slug, final String expectedName) {
            assertThat(map(slug))
                .extracting(SourceBook::authorNames, as(InstanceOfAssertFactories.list(String.class)))
                .as("only the primary name is taken")
                .containsExactly(expectedName);
        }

        @Test
        @DisplayName("takes the leading page count ahead of a later plate count")
        void takesPageCountAheadOfPlateCount() {
            assertThat(map(HOBBIT).pageCount())
                .as("272 pages, then 36 pages of plates, parses to 272")
                .isEqualTo(HOBBIT_PAGES);
        }

        @Test
        @DisplayName("picks the series title carrying the part number over the imprint title")
        void picksTheNumberedSeriesTitle() {
            assertThat(map(EYE).seriesName())
                .as("Wheel of time carries bk. 1; TOR fantasy has no part number")
                .isEqualTo(WHEEL_OF_TIME);
        }

        @Test
        @DisplayName("carries the MARC 520 summary as the description")
        void carriesTheSummary() {
            assertThat(map(DUNE).description())
                .startsWith("Follows the adventures of Paul Atreides");
        }

        @Test
        @DisplayName("reduced genres land in rawSubjects, the slot the catalog persists")
        void genresLandInRawSubjects() {
            final SourceBook book = map(DUNE);

            assertThat(book.rawSubjects())
                .as("the lcgft and fast genre elements reduce to canonical genres")
                .containsExactlyInAnyOrder("science fiction", "fiction");
            assertThat(book.rawCategories())
                .as("genres must not be parked in rawCategories, which applyFrom ignores")
                .isNull();
        }
    }
}
