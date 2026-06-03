package com.betterreads.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SourceMerger}. The merger combines several single-source books into one,
 * resolving each field by the priority order in {@code source-trust.md}: subjects are unioned across
 * every source, every other field takes the first source in its chain that supplies a value.
 */
class SourceMergerTest {

    private static final int ORIGINAL_YEAR = 1965;

    private static final int REPRINT_YEAR = 2016;

    private static final double GOOGLE_RATING = 3.0;

    private static final int GOOGLE_RATING_COUNT = 10;

    private static final double HARDCOVER_RATING = 4.32;

    private static final int HARDCOVER_RATING_COUNT = 5000;

    private static final String TITLE = "Dune";

    private static final String EDITION_TITLE = "Dune (2019 Edition)";

    private static final String SERIES = "Dune Saga";

    private static final String SCIENCE_FICTION = "science fiction";

    private static final String FANTASY = "fantasy";

    private static final String FICTION = "fiction";

    private static final String COVER_URL = "https://covers.openlibrary.org/b/id/1-L.jpg";

    private static final String HUGO = "Hugo Award";

    private static final String NEBULA = "Nebula Award";

    private static final String GOOGLE_ID = "gb-1";

    private static final String ISBN = "9780441013593";

    private static final String WIKIDATA_QID = "Q190192";

    private final SourceMerger merger = new SourceMerger();

    @Nested
    @DisplayName("single-winner fields")
    class SingleWinner {

        @Test
        @DisplayName("title comes from Google before OpenLibrary")
        void titlePrefersGoogle() {
            final String googleTitle = "Dune: The Graphic Novel";
            final SourceBook google = SourceBook.builder(BookFieldSource.GOOGLE_BOOKS)
                .title(googleTitle)
                .build();
            final SourceBook openLibrary = SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
                .title(TITLE)
                .build();

            final MergedBook merged = merger.merge(List.of(openLibrary, google));

            assertThat(merged.book().title()).isEqualTo(googleTitle);
        }

        @Test
        @DisplayName("the merged title is cleaned of edition tags")
        void mergedTitleIsCleaned() {
            final SourceBook google = SourceBook.builder(BookFieldSource.GOOGLE_BOOKS)
                .title(EDITION_TITLE)
                .build();

            final MergedBook merged = merger.merge(List.of(google));

            assertThat(merged.book().title())
                .as("Google's (2019 Edition) tag must not reach the stored title")
                .isEqualTo(TITLE);
        }

        @Test
        @DisplayName("title falls back to OpenLibrary when Google has none")
        void titleFallsBackToOpenLibrary() {
            final SourceBook openLibrary = SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
                .title(TITLE)
                .build();
            final SourceBook google = SourceBook.builder(BookFieldSource.GOOGLE_BOOKS)
                .build();

            final MergedBook merged = merger.merge(List.of(google, openLibrary));

            assertThat(merged.book().title())
                .as("a higher-priority null must not beat a lower-priority real value")
                .isEqualTo(TITLE);
        }

        @Test
        @DisplayName("cover comes from OpenLibrary before Hardcover")
        void coverPrefersOpenLibrary() {
            final SourceBook openLibrary = SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
                .title(TITLE)
                .coverUrl(COVER_URL)
                .build();
            final SourceBook hardcover = SourceBook.builder(BookFieldSource.HARDCOVER)
                .title(TITLE)
                .coverUrl("https://assets.hardcover.app/dune.jpg")
                .build();

            final MergedBook merged = merger.merge(List.of(hardcover, openLibrary));

            assertThat(merged.book().coverUrl()).isEqualTo(COVER_URL);
        }

        @Test
        @DisplayName("publication year comes from OpenLibrary's first-publish year before Google's edition year")
        void yearPrefersOpenLibrary() {
            final SourceBook google = SourceBook.builder(BookFieldSource.GOOGLE_BOOKS)
                .title(TITLE)
                .publicationYear(REPRINT_YEAR)
                .build();
            final SourceBook openLibrary = SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
                .title(TITLE)
                .publicationYear(ORIGINAL_YEAR)
                .build();

            final MergedBook merged = merger.merge(List.of(google, openLibrary));

            assertThat(merged.book().publicationYear())
                .as("OpenLibrary gives the original year; Google gives the reprint year")
                .isEqualTo(ORIGINAL_YEAR);
        }

        @Test
        @DisplayName("rating comes only from Hardcover")
        void ratingComesFromHardcover() {
            final SourceBook google = SourceBook.builder(BookFieldSource.GOOGLE_BOOKS)
                .title(TITLE)
                .averageRating(GOOGLE_RATING)
                .ratingCount(GOOGLE_RATING_COUNT)
                .build();
            final SourceBook hardcover = SourceBook.builder(BookFieldSource.HARDCOVER)
                .title(TITLE)
                .averageRating(HARDCOVER_RATING)
                .ratingCount(HARDCOVER_RATING_COUNT)
                .build();

            final MergedBook merged = merger.merge(List.of(google, hardcover));

            assertThat(merged.book().averageRating())
                .as("Hardcover is the sole rating source; Google's rating is not trusted")
                .isEqualTo(HARDCOVER_RATING);
        }

        @Test
        @DisplayName("series comes from Hardcover before Wikidata")
        void seriesPrefersHardcover() {
            final SourceBook wikidata = SourceBook.builder(BookFieldSource.WIKIDATA)
                .title(TITLE)
                .seriesName(SERIES)
                .build();
            final SourceBook hardcover = SourceBook.builder(BookFieldSource.HARDCOVER)
                .title(TITLE)
                .seriesName(TITLE)
                .build();

            final MergedBook merged = merger.merge(List.of(wikidata, hardcover));

            assertThat(merged.book().seriesName())
                .as("the series comes from Hardcover when both Hardcover and Wikidata have it")
                .isEqualTo(TITLE);
        }

        @Test
        @DisplayName("series falls back to Wikidata when Hardcover has none")
        void seriesFallsBackToWikidata() {
            final SourceBook hardcover = SourceBook.builder(BookFieldSource.HARDCOVER)
                .title(TITLE)
                .build();
            final SourceBook wikidata = SourceBook.builder(BookFieldSource.WIKIDATA)
                .title(TITLE)
                .seriesName(SERIES)
                .build();

            final MergedBook merged = merger.merge(List.of(hardcover, wikidata));

            assertThat(merged.book().seriesName()).isEqualTo(SERIES);
        }

        @Test
        @DisplayName("awards come only from Wikidata")
        void awardsComeFromWikidata() {
            final SourceBook wikidata = SourceBook.builder(BookFieldSource.WIKIDATA)
                .title(TITLE)
                .awards(List.of(HUGO, NEBULA))
                .build();

            final MergedBook merged = merger.merge(List.of(wikidata));

            assertThat(merged.book().awards()).containsExactly(HUGO, NEBULA);
        }
    }

    @Nested
    @DisplayName("description floor")
    class Description {

        @Test
        @DisplayName("Google boilerplate under the floor yields to OpenLibrary's real prose")
        void boilerplateYieldsToRealProse() {
            final SourceBook google = SourceBook.builder(BookFieldSource.GOOGLE_BOOKS)
                .title(TITLE)
                .description("A Tom Doherty book.")
                .build();
            final SourceBook openLibrary = SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
                .title(TITLE)
                .description("A desert planet holds the universe's only source of the spice melange.")
                .build();

            final MergedBook merged = merger.merge(List.of(google, openLibrary));

            assertThat(merged.book().description())
                .as("a description under the 20-char floor is skipped so the next source's prose is used")
                .startsWith("A desert planet");
        }
    }

    @Nested
    @DisplayName("unioned subjects")
    class Subjects {

        @Test
        @DisplayName("subjects from every source are combined and deduplicated")
        void subjectsAreUnioned() {
            final SourceBook openLibrary = SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
                .title(TITLE)
                .rawSubjects(List.of(SCIENCE_FICTION, FICTION))
                .build();
            final SourceBook wikidata = SourceBook.builder(BookFieldSource.WIKIDATA)
                .title(TITLE)
                .rawSubjects(List.of(SCIENCE_FICTION, FANTASY))
                .build();

            final MergedBook merged = merger.merge(List.of(openLibrary, wikidata));

            assertThat(merged.book().rawSubjects())
                .as("genres from all sources combine; the shared 'science fiction' appears once")
                .containsExactlyInAnyOrder(SCIENCE_FICTION, FICTION, FANTASY);
        }

        @Test
        @DisplayName("subject provenance lists every contributing source")
        void subjectProvenanceListsContributors() {
            final SourceBook openLibrary = SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
                .title(TITLE)
                .rawSubjects(List.of(SCIENCE_FICTION))
                .build();
            final SourceBook wikidata = SourceBook.builder(BookFieldSource.WIKIDATA)
                .title(TITLE)
                .rawSubjects(List.of(FANTASY))
                .build();

            final MergedBook merged = merger.merge(List.of(openLibrary, wikidata));

            assertThat(merged.subjectSources())
                .as("subjects are unioned, so provenance is the set of contributors, not one winner")
                .containsExactlyInAnyOrder(BookFieldSource.OPEN_LIBRARY, BookFieldSource.WIKIDATA);
        }
    }

    @Nested
    @DisplayName("provenance and identity")
    class Provenance {

        @Test
        @DisplayName("provenance records which source each single-winner field came from")
        void provenanceRecordsWinner() {
            final SourceBook google = SourceBook.builder(BookFieldSource.GOOGLE_BOOKS)
                .title(EDITION_TITLE)
                .build();
            final SourceBook openLibrary = SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
                .title(TITLE)
                .publicationYear(ORIGINAL_YEAR)
                .build();

            final MergedBook merged = merger.merge(List.of(google, openLibrary));

            assertThat(merged.provenanceOf(BookField.TITLE)).isEqualTo(BookFieldSource.GOOGLE_BOOKS);
            assertThat(merged.provenanceOf(BookField.PUBLICATION_YEAR)).isEqualTo(BookFieldSource.OPEN_LIBRARY);
        }

        @Test
        @DisplayName("a field no source supplies stays null with no provenance")
        void absentFieldStaysNull() {
            final SourceBook openLibrary = SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
                .title(TITLE)
                .build();

            final MergedBook merged = merger.merge(List.of(openLibrary));

            assertThat(merged.book().description()).isNull();
            assertThat(merged.provenanceOf(BookField.DESCRIPTION)).isNull();
        }

        @Test
        @DisplayName("every source identifier is carried onto the merged book")
        void identifiersAreCarried() {
            final SourceBook google = SourceBook.builder(BookFieldSource.GOOGLE_BOOKS)
                .title(TITLE)
                .googleBooksVolumeId(GOOGLE_ID)
                .isbn13(ISBN)
                .build();
            final SourceBook wikidata = SourceBook.builder(BookFieldSource.WIKIDATA)
                .title(TITLE)
                .wikidataQid(WIKIDATA_QID)
                .build();

            final MergedBook merged = merger.merge(List.of(google, wikidata));

            assertThat(merged.book().googleBooksVolumeId()).isEqualTo(GOOGLE_ID);
            assertThat(merged.book().wikidataQid()).isEqualTo(WIKIDATA_QID);
            assertThat(merged.book().isbn13()).isEqualTo(ISBN);
        }
    }
}
