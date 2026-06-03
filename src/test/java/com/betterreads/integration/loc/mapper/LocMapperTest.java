package com.betterreads.integration.loc.mapper;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;

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

/**
 * Maps a MODS record onto a {@link SourceBook}.
 *
 * <p>Each record is an inline SRU response holding only the elements under test, in the {@code zs:}
 * wrapper and MODS namespace the parser navigates.
 */
class LocMapperTest {

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

    private static final String DUNE = sru("""
        <titleInfo><title>Dune</title></titleInfo>
        <name type="personal" usage="primary"><namePart>Herbert, Frank,</namePart></name>
        <genre authority="lcgft">Science fiction.</genre>
        <genre authority="fast">Fiction.</genre>
        <originInfo><dateIssued encoding="marc">2019</dateIssued></originInfo>
        <physicalDescription><extent>xxii, 658 pages : map ; 24 cm.</extent></physicalDescription>
        <abstract type="Summary">Follows the adventures of Paul Atreides, the son of a betrayed duke.</abstract>
        <relatedItem type="series"><titleInfo>
        <title>Dune chronicles</title><partNumber>bk. 1</partNumber></titleInfo></relatedItem>
        <identifier type="isbn">9780593099322</identifier>
        <identifier type="isbn">059309932X</identifier>
        <identifier type="lccn">2019287107</identifier>""");

    private static final String WATCHMEN = sru("""
        <titleInfo><title>Watchmen</title></titleInfo>
        <name type="personal" usage="primary"><namePart>Moore, Alan,</namePart></name>
        <originInfo><dateIssued encoding="marc">2013</dateIssued></originInfo>
        <physicalDescription><extent>414 pages : chiefly illustrations ; 26 cm.</extent></physicalDescription>
        <identifier type="isbn">9781401238964</identifier>
        <identifier type="lccn">2013003992</identifier>""");

    private static final String HOBBIT = sru("""
        <titleInfo><title>The Hobbit</title></titleInfo>
        <name type="personal" usage="primary"><namePart>Tolkien, J. R. R. (John Ronald Reuel),</namePart></name>
        <originInfo><dateIssued encoding="marc">2023</dateIssued></originInfo>
        <physicalDescription>
        <extent>xxxix, 272 pages, 36 pages of plates : illustrations ; 23 cm.</extent></physicalDescription>
        <identifier type="isbn">9780063347533</identifier>
        <identifier type="lccn">2024442463</identifier>""");

    private static final String SANDMAN = sru("""
        <titleInfo><title>The Sandman</title></titleInfo>
        <name type="personal" usage="primary"><namePart>Gaiman, Neil,</namePart></name>
        <originInfo><dateIssued encoding="marc">1991</dateIssued></originInfo>
        <physicalDescription><extent>1 v. (unpaged) : col. ill. ; 26 cm.</extent></physicalDescription>
        <identifier type="isbn">9781563890116</identifier>
        <identifier type="lccn">92159876</identifier>""");

    private static final String EYE = sru("""
        <titleInfo><title>The Eye of the World</title></titleInfo>
        <name type="personal" usage="primary"><namePart>Jordan, Robert.</namePart></name>
        <physicalDescription><extent>xiv, 670 p., [3] p. of plates : maps ; 24 cm.</extent></physicalDescription>
        <relatedItem type="series"><titleInfo><title>TOR fantasy</title></titleInfo></relatedItem>
        <relatedItem type="series"><titleInfo>
        <title>Wheel of time</title><partNumber>bk. 1</partNumber></titleInfo></relatedItem>
        <identifier type="isbn">0312850093 :</identifier>
        <identifier type="isbn">9780312850098</identifier>
        <identifier type="lccn">89007939</identifier>""");

    private static final String CLASH = sru("""
        <titleInfo><title>A Clash of Kings</title></titleInfo>
        <name type="personal" usage="primary"><namePart>Martin, George R. R.</namePart></name>
        <physicalDescription><extent>761 p. : ill. ; 25 cm.</extent></physicalDescription>
        <relatedItem type="series"><titleInfo>
        <title>Song of ice and fire</title><partNumber>bk. 2</partNumber></titleInfo></relatedItem>
        <identifier type="isbn">0553108034</identifier>
        <identifier type="isbn">9780553108033</identifier>
        <identifier type="lccn">98037954</identifier>""");

    private final LocMapper mapper = new LocMapper();

    private static String sru(final String modsBody) {
        return """
            <?xml version="1.0"?>
            <zs:searchRetrieveResponse xmlns:zs="http://www.loc.gov/zing/srw/"><zs:records><zs:record>\
            <zs:recordData><mods xmlns="http://www.loc.gov/mods/v3" version="3.8">
            """ + modsBody + """
            </mods></zs:recordData></zs:record></zs:records></zs:searchRetrieveResponse>""";
    }

    private record Expected(
        String record,
        String name,
        String lccn,
        String isbn13,
        Integer marcYear,
        Integer pageCount,
        String seriesName,
        Integer seriesPosition
    ) { }

    private SourceBook map(final String record) {
        return mapper.toSourceBook(record).orElseThrow();
    }

    @Nested
    @DisplayName("toSourceBook")
    class ToSourceBook {

        static Stream<Arguments> slate() {
            return Stream.of(
                Arguments.of("dune", new Expected(DUNE, "Herbert, Frank", "2019287107",
                    "9780593099322", DUNE_YEAR, DUNE_PAGES, "Dune chronicles", 1)),
                Arguments.of("watchmen", new Expected(WATCHMEN, "Moore, Alan", "2013003992",
                    "9781401238964", WATCHMEN_YEAR, WATCHMEN_PAGES, null, null)),
                Arguments.of("hobbit", new Expected(HOBBIT, "Tolkien, J. R. R. (John Ronald Reuel)",
                    "2024442463", "9780063347533", HOBBIT_YEAR, HOBBIT_PAGES, null, null)),
                Arguments.of("sandman", new Expected(SANDMAN, "Gaiman, Neil", "92159876",
                    "9781563890116", SANDMAN_YEAR, null, null, null)),
                Arguments.of("eye", new Expected(EYE, "Jordan, Robert", "89007939",
                    "9780312850098", null, EYE_PAGES, WHEEL_OF_TIME, 1)),
                Arguments.of("clash", new Expected(CLASH, "Martin, George R. R.", "98037954",
                    "9780553108033", null, CLASH_PAGES, "Song of ice and fire", CLASH_POSITION)));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("slate")
        @DisplayName("each record yields its lccn, isbn-13, marc year, page count, and series")
        void parsesEachRecord(final String name, final Expected expected) {
            final SourceBook book = map(expected.record());

            assertThat(book.source()).isEqualTo(BookFieldSource.LOC);
            assertThat(book.locLccn()).isEqualTo(expected.lccn());
            assertThat(book.isbn13())
                .as("the 978 isbn is picked from the interleaved identifiers")
                .isEqualTo(expected.isbn13());
            assertThat(book.publicationYear())
                .as("the marc-encoded year, or null when the record has none")
                .isEqualTo(expected.marcYear());
            assertThat(book.pageCount())
                .as("the page count from the extent, or null for an unpaged extent")
                .isEqualTo(expected.pageCount());
            assertThat(book.seriesName()).isEqualTo(expected.seriesName());
            assertThat(book.seriesPosition()).isEqualTo(expected.seriesPosition());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("slate")
        @DisplayName("strips a trailing comma or period from the primary author, keeps an initial")
        void normalizesPrimaryAuthorName(final String name, final Expected expected) {
            assertThat(map(expected.record()))
                .extracting(SourceBook::authorNames, as(InstanceOfAssertFactories.list(String.class)))
                .as("only the primary name is taken, trailing punctuation stripped")
                .containsExactly(expected.name());
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
        }
    }
}
