package com.betterreads.bookmerge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.betterreads.booksource.BookField;
import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.MergedBook;
import com.betterreads.booksource.SeriesEntry;
import com.betterreads.booksource.SourceAuthor;
import com.betterreads.booksource.SourceBook;
import com.betterreads.testsupport.Books;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class SourceMergerTest {

    private static final int ORIGINAL_YEAR = 1965;

    private static final int REPRINT_YEAR = 2016;

    private static final double GOOGLE_RATING = 3.0;

    private static final int GOOGLE_RATING_COUNT = 10;

    private static final double HARDCOVER_RATING = 4.32;

    private static final int HARDCOVER_RATING_COUNT = 5000;

    private static final String TITLE = Books.DUNE_TITLE;

    private static final String EDITION_TITLE = "Dune (2019 Edition)";

    private static final String SERIES = "Dune Saga";

    private static final int HARDCOVER_VOLUME = 1;

    private static final int WIKIDATA_VOLUME = 3;

    private static final String UMBRELLA_SERIES = "Dune Universe";

    private static final int UMBRELLA_VOLUME = 7;

    private static final String SCIENCE_FICTION = "science fiction";

    private static final String FANTASY = "fantasy";

    private static final String FICTION = "fiction";

    private static final String COVER_URL = "https://covers.example.test/1-L.jpg";

    private static final String HUGO = "Hugo Award";

    private static final String NEBULA = "Nebula Award";

    private static final String GOOGLE_ID = "gb-1";

    private static final String ISBN = Books.DUNE_ISBN;

    private static final String WIKIDATA_QID = Books.DUNE_QID;

    private static final String CAPITALIZED_TITLE = "The Night Is Watching";

    private static final String LOWERCASE_TITLE = "The night is watching";

    private final SourceMerger merger = new SourceMerger();

    private static SourceBook.Builder titled(final BookFieldSource source) {
        return SourceBook.builder(source).title(TITLE);
    }

    private static SourceBook wikidataVolume() {
        return titled(BookFieldSource.WIKIDATA).seriesName(SERIES).seriesPosition(WIKIDATA_VOLUME).build();
    }

    // PMD.TooManyMethods: one test per merged field and source priority
    @SuppressWarnings("PMD.TooManyMethods")
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
            final SourceBook openLibrary = titled(BookFieldSource.OPEN_LIBRARY)
                .build();

            final MergedBook merged = merger.merge(null, List.of(openLibrary, google));

            assertThat(merged.book().title()).isEqualTo(googleTitle);
        }

        @Test
        void shouldPreferHardcoverAuthorsOverOpenLibrary() {
            final String author = "Patrick Rothfuss";
            final SourceBook hardcover = titled(BookFieldSource.HARDCOVER)
                .authors(SourceAuthor.ofNames(List.of(author)))
                .build();
            final SourceBook openLibrary = titled(BookFieldSource.OPEN_LIBRARY)
                .authors(SourceAuthor.ofNames(List.of(author, "Marc Simonetti")))
                .build();

            final MergedBook merged = merger.merge(null, List.of(openLibrary, hardcover));

            assertThat(merged.book().authors())
                .extracting(SourceAuthor::name)
                .containsExactly(author);
        }

        @Test
        void shouldUseNextSourceTitleWhenFirstIsBlank() {
            final SourceBook google = SourceBook.builder(BookFieldSource.GOOGLE_BOOKS)
                .title(" ")
                .build();
            final SourceBook openLibrary = titled(BookFieldSource.OPEN_LIBRARY)
                .build();

            final MergedBook merged = merger.merge(null, List.of(google, openLibrary));

            assertThat(merged.book().title()).isEqualTo(TITLE);
        }

        @Test
        void shouldUseNextSourceAuthorsWhenFirstListIsEmpty() {
            final String darrow = "Darrow";
            final SourceBook hardcover = titled(BookFieldSource.HARDCOVER)
                .authors(List.of())
                .build();
            final SourceBook google = titled(BookFieldSource.GOOGLE_BOOKS)
                .authors(SourceAuthor.ofNames(List.of(darrow)))
                .build();

            final MergedBook merged = merger.merge(null, List.of(hardcover, google));

            assertThat(merged.book().authors())
                .extracting(SourceAuthor::name)
                .containsExactly(darrow);
        }

        @Test
        void shouldTakeLanguageFromIsbnWhenNoSourceHasOne() {
            final SourceBook openLibrary = titled(BookFieldSource.OPEN_LIBRARY)
                .isbn13("9781439153956")
                .build();

            final MergedBook merged = merger.merge(null, List.of(openLibrary));

            assertThat(merged.book().language()).isEqualTo("en");
        }

        @Test
        void shouldPreferSourceLanguageOverIsbn() {
            final SourceBook openLibrary = titled(BookFieldSource.OPEN_LIBRARY)
                .isbn13(ISBN)
                .language("spa")
                .build();

            final MergedBook merged = merger.merge(null, List.of(openLibrary));

            assertThat(merged.book().language()).isEqualTo("es");
        }

        @Test
        void shouldTakeTheCapitalizedTitleFromAnotherSource() {
            final SourceBook openLibrary = SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
                .title(LOWERCASE_TITLE)
                .build();
            final SourceBook hardcover = SourceBook.builder(BookFieldSource.HARDCOVER)
                .title(CAPITALIZED_TITLE)
                .build();

            final MergedBook merged = merger.merge(null, List.of(openLibrary, hardcover));

            assertThat(merged.book().title()).isEqualTo(CAPITALIZED_TITLE);
        }

        @Test
        void shouldTitleCaseALowercaseEnglishTitle() {
            final SourceBook openLibrary = SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
                .title(LOWERCASE_TITLE)
                .isbn13("9780778315063")
                .build();

            final MergedBook merged = merger.merge(null, List.of(openLibrary));

            assertThat(merged.book().title()).isEqualTo(CAPITALIZED_TITLE);
        }

        @Test
        void shouldTitleCaseASpacedCatalogTitle() {
            final SourceBook openLibrary = SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
                .title("Fathomless riches ; or how I went from pop to pulpit")
                .isbn13("9781780226194")
                .build();

            final MergedBook merged = merger.merge(null, List.of(openLibrary));

            assertThat(merged.book().title()).isEqualTo("Fathomless Riches: Or How I Went From Pop to Pulpit");
        }

        @Test
        void shouldTakeTheCapitalizedTitleFromASpacedCatalogTitle() {
            final SourceBook google = SourceBook.builder(BookFieldSource.GOOGLE_BOOKS)
                .title("Batman: the long Halloween")
                .build();
            final SourceBook loc = SourceBook.builder(BookFieldSource.LOC)
                .title("Batman : The Long Halloween")
                .build();

            final MergedBook merged = merger.merge(null, List.of(google, loc));

            assertThat(merged.book().title()).isEqualTo("Batman: The Long Halloween");
        }

        @Test
        @DisplayName("the merged title is cleaned of edition tags")
        void mergedTitleIsCleaned() {
            final SourceBook google = SourceBook.builder(BookFieldSource.GOOGLE_BOOKS)
                .title(EDITION_TITLE)
                .build();

            final MergedBook merged = merger.merge(null, List.of(google));

            assertThat(merged.book().title())
                .as("should strip Google's (2019 Edition) tag from the stored title")
                .isEqualTo(TITLE);
        }

        @Test
        @DisplayName("title falls back to OpenLibrary when Google has none")
        void titleFallsBackToOpenLibrary() {
            final SourceBook openLibrary = titled(BookFieldSource.OPEN_LIBRARY)
                .build();
            final SourceBook google = SourceBook.builder(BookFieldSource.GOOGLE_BOOKS)
                .build();

            final MergedBook merged = merger.merge(null, List.of(google, openLibrary));

            assertThat(merged.book().title())
                .as("should skip a higher-priority null for a lower-priority real value")
                .isEqualTo(TITLE);
        }

        @Test
        @DisplayName("a merge where no source carries a title is rejected")
        void mergeWithoutATitleIsRejected() {
            final SourceBook untitled = SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
                .isbn13(ISBN)
                .build();

            assertThatThrownBy(() -> merger.merge(null, List.of(untitled)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title");
        }

        @Test
        @DisplayName("cover comes from Google's edition before OpenLibrary's work-level cover")
        void coverPrefersGoogleOverOpenLibrary() {
            final SourceBook google = titled(BookFieldSource.GOOGLE_BOOKS)
                .coverUrl(COVER_URL)
                .build();
            final SourceBook openLibrary = titled(BookFieldSource.OPEN_LIBRARY)
                .coverUrl("https://covers.example.test/2-L.jpg")
                .build();

            final MergedBook merged = merger.merge(null, List.of(openLibrary, google));

            assertThat(merged.book().coverUrl())
                .as("should take Google's cover because it matches the edition's language")
                .isEqualTo(COVER_URL);
        }

        @Test
        @DisplayName("publication year comes from OpenLibrary's first-publish year before Google's edition year")
        void yearPrefersOpenLibrary() {
            final SourceBook google = titled(BookFieldSource.GOOGLE_BOOKS)
                .publicationYear(REPRINT_YEAR)
                .build();
            final SourceBook openLibrary = titled(BookFieldSource.OPEN_LIBRARY)
                .publicationYear(ORIGINAL_YEAR)
                .build();

            final MergedBook merged = merger.merge(null, List.of(google, openLibrary));

            assertThat(merged.book().publicationYear())
                .as("should take OpenLibrary's first-publish year")
                .isEqualTo(ORIGINAL_YEAR);
        }

        @Test
        @DisplayName("the discovery seed's year wins over a later edition another source resolved")
        void seedYearWinsOverEditionDrift() {
            final SourceBook hardcoverSeed = titled(BookFieldSource.HARDCOVER)
                .publicationYear(ORIGINAL_YEAR)
                .build();
            final SourceBook openLibrary = titled(BookFieldSource.OPEN_LIBRARY)
                .publicationYear(REPRINT_YEAR)
                .build();

            final MergedBook merged = merger.merge(hardcoverSeed, List.of(hardcoverSeed, openLibrary));

            assertThat(merged.book().publicationYear())
                .as("should keep the seed's year over a drifted reprint year")
                .isEqualTo(ORIGINAL_YEAR);
        }

        @Test
        @DisplayName("with no seed year the year chain still resolves the year")
        void seedWithoutYearFallsBackToChain() {
            final SourceBook hardcoverSeed = titled(BookFieldSource.HARDCOVER)
                .build();
            final SourceBook openLibrary = titled(BookFieldSource.OPEN_LIBRARY)
                .publicationYear(ORIGINAL_YEAR)
                .build();

            final MergedBook merged = merger.merge(hardcoverSeed, List.of(hardcoverSeed, openLibrary));

            assertThat(merged.book().publicationYear())
                .as("should fall back to OpenLibrary's year when the seed has none")
                .isEqualTo(ORIGINAL_YEAR);
        }

        @Test
        @DisplayName("rating comes only from Hardcover")
        void ratingComesFromHardcover() {
            final SourceBook google = titled(BookFieldSource.GOOGLE_BOOKS)
                .averageRating(GOOGLE_RATING)
                .ratingCount(GOOGLE_RATING_COUNT)
                .build();
            final SourceBook hardcover = titled(BookFieldSource.HARDCOVER)
                .averageRating(HARDCOVER_RATING)
                .ratingCount(HARDCOVER_RATING_COUNT)
                .build();

            final MergedBook merged = merger.merge(null, List.of(google, hardcover));

            assertThat(merged.book().averageRating())
                .as("should take the rating only from Hardcover")
                .isEqualTo(HARDCOVER_RATING);
        }

        @Test
        @DisplayName("series comes from Hardcover before Wikidata")
        void seriesPrefersHardcover() {
            final SourceBook wikidata = wikidataVolume();
            final SourceBook hardcover = titled(BookFieldSource.HARDCOVER)
                .seriesName(TITLE)
                .seriesPosition(HARDCOVER_VOLUME)
                .build();

            final MergedBook merged = merger.merge(null, List.of(wikidata, hardcover));

            assertThat(merged.book()).satisfies(book -> {
                assertThat(book.seriesName())
                    .as("should take Hardcover's series when Wikidata also has one")
                    .isEqualTo(TITLE);
                assertThat(book.seriesPosition()).isEqualTo(HARDCOVER_VOLUME);
            });
        }

        @Test
        void shouldTakeEverySeriesFromHardcover() {
            final List<SeriesEntry> memberships = List.of(
                new SeriesEntry(SERIES, HARDCOVER_VOLUME), new SeriesEntry(UMBRELLA_SERIES, UMBRELLA_VOLUME));
            final SourceBook hardcover = titled(BookFieldSource.HARDCOVER)
                .seriesName(SERIES)
                .seriesPosition(HARDCOVER_VOLUME)
                .series(memberships)
                .build();

            final MergedBook merged = merger.merge(null, List.of(wikidataVolume(), hardcover));

            assertThat(merged.book().series()).isEqualTo(memberships);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = TITLE)
        void shouldTakeTheWikidataVolumeWhenHardcoverHasNoNumberedSeries(final String hardcoverSeriesName) {
            final SourceBook hardcover = titled(BookFieldSource.HARDCOVER).seriesName(hardcoverSeriesName).build();

            final MergedBook merged = merger.merge(null, List.of(hardcover, wikidataVolume()));

            assertThat(merged.book()).satisfies(book -> {
                assertThat(book.seriesName()).isEqualTo(SERIES);
                assertThat(book.seriesPosition()).isEqualTo(WIKIDATA_VOLUME);
            });
        }

        @Test
        void shouldDropASingleIssuesSeries() {
            final SourceBook hardcover = SourceBook.builder(BookFieldSource.HARDCOVER)
                .title("Absolute Batman Vol. 2: Abomination")
                .seriesName("Absolute Batman (2024) (Single Issues)")
                .seriesPosition(HARDCOVER_VOLUME)
                .build();

            final MergedBook merged = merger.merge(null, List.of(hardcover));

            assertThat(merged.book().seriesName()).isNull();
            assertThat(merged.book().series()).isEmpty();
        }

        @Test
        void shouldSkipASeriesNamedAfterTheBookItself() {
            final String beginnings = "The Dark Tower: Beginnings";
            final String treachery = "Treachery";
            final SourceBook hardcover = SourceBook.builder(BookFieldSource.HARDCOVER)
                .title("Treachery (Deluxe Edition)")
                .seriesName("The Dark Tower: Treachery")
                .seriesPosition(HARDCOVER_VOLUME)
                .build();
            final SourceBook wikidata = SourceBook.builder(BookFieldSource.WIKIDATA)
                .title(treachery)
                .seriesName(beginnings)
                .seriesPosition(WIKIDATA_VOLUME)
                .build();

            final MergedBook merged = merger.merge(null, List.of(hardcover, wikidata));

            assertThat(merged.book().seriesName()).isEqualTo(beginnings);
            assertThat(merged.book().series()).containsExactly(new SeriesEntry(beginnings, WIKIDATA_VOLUME));
        }

        @Test
        void shouldAddLocCoAuthorsAfterTheChosenAuthors() {
            final String martin = "George R.R. Martin";
            final String dozois = "Gardner Dozois";

            final List<String> authors = mergedAuthors(List.of(martin), List.of("George R. R. Martin", dozois));

            assertThat(authors).containsExactly(martin, dozois);
        }

        @Test
        void shouldIgnoreLocAuthorsThatDoNotIncludeTheChosenAuthors() {
            final String cart = "Michael Cart";

            final List<String> authors = mergedAuthors(List.of(cart), List.of("Christine Jenkins"));

            assertThat(authors).containsExactly(cart);
        }

        private List<String> mergedAuthors(final List<String> hardcoverAuthors, final List<String> locAuthors) {
            final SourceBook hardcover = titled(BookFieldSource.HARDCOVER)
                .authors(SourceAuthor.ofNames(hardcoverAuthors))
                .build();
            final SourceBook loc = titled(BookFieldSource.LOC)
                .authors(SourceAuthor.ofNames(locAuthors))
                .build();
            return merger.merge(null, List.of(hardcover, loc)).book().authors().stream()
                .map(SourceAuthor::name)
                .toList();
        }

        @Test
        @DisplayName("Wikidata awards are carried onto the merged book")
        void awardsComeFromWikidata() {
            final SourceBook wikidata = titled(BookFieldSource.WIKIDATA)
                .awards(List.of(HUGO, NEBULA))
                .build();

            final MergedBook merged = merger.merge(null, List.of(wikidata));

            assertThat(merged.book().awards()).containsExactly(HUGO, NEBULA);
        }
    }

    @Nested
    @DisplayName("description quality")
    class Description {

        private static final String BOLD = "***";

        private static final String REAL_PROSE =
            "A desert planet holds the universe's only source of the spice melange, and the boy "
            + "Paul Atreides must master it to survive the war over Arrakis.";

        @Test
        void shouldPreferOpenLibraryProseOverAWeakerGoogleDescription() {
            final String googleDescription = "A short but passable blurb about a desert planet and its spice.";
            final SourceBook google = titled(BookFieldSource.GOOGLE_BOOKS).description(googleDescription).build();
            final SourceBook openLibrary = titled(BookFieldSource.OPEN_LIBRARY).description(REAL_PROSE).build();

            final MergedBook merged = merger.merge(null, List.of(google, openLibrary));

            assertThat(merged.book().description()).isEqualTo(REAL_PROSE);
        }

        @Test
        @DisplayName("a quality tie breaks toward the higher-priority source in the chain")
        void tieBreaksTowardHigherPrioritySource() {
            final SourceBook google = titled(BookFieldSource.GOOGLE_BOOKS)
                .description(REAL_PROSE)
                .build();
            final SourceBook openLibrary = titled(BookFieldSource.OPEN_LIBRARY)
                .description(REAL_PROSE)
                .build();

            final MergedBook merged = merger.merge(null, List.of(openLibrary, google));

            assertThat(merged.provenanceOf(BookField.DESCRIPTION))
                .as("should break an equal-quality tie toward Google")
                .isEqualTo(BookFieldSource.GOOGLE_BOOKS);
        }

        @Test
        void shouldLeaveDescriptionEmptyWhenNoSourceIsUsable() {
            final SourceBook openLibrary = titled(BookFieldSource.OPEN_LIBRARY)
                .description("A short blurb.")
                .build();

            final MergedBook merged = merger.merge(null, List.of(openLibrary));

            assertThat(merged.book().description()).isNull();
        }

        @Test
        @DisplayName("the stored description is stripped of Markdown markup")
        void storedDescriptionIsCleaned() {
            final SourceBook openLibrary = titled(BookFieldSource.OPEN_LIBRARY)
                .description(BOLD + REAL_PROSE + BOLD)
                .build();

            final MergedBook merged = merger.merge(null, List.of(openLibrary));

            assertThat(merged.book().description()).isEqualTo(REAL_PROSE);
        }
    }

    @Nested
    @DisplayName("subject union")
    class Subjects {

        @Test
        @DisplayName("subjects from every source are combined and deduplicated")
        void subjectsAreUnioned() {
            final SourceBook openLibrary = titled(BookFieldSource.OPEN_LIBRARY)
                .rawSubjects(List.of(SCIENCE_FICTION, FICTION))
                .build();
            final SourceBook wikidata = titled(BookFieldSource.WIKIDATA)
                .rawSubjects(List.of(SCIENCE_FICTION, FANTASY))
                .build();

            final MergedBook merged = merger.merge(null, List.of(openLibrary, wikidata));

            assertThat(merged.book().rawSubjects())
                .as("should combine genres from all sources and list a shared one once")
                .containsExactlyInAnyOrder(SCIENCE_FICTION, FICTION, FANTASY);
        }

        @Test
        @DisplayName("subject provenance lists every contributing source")
        void subjectProvenanceListsContributors() {
            final SourceBook openLibrary = titled(BookFieldSource.OPEN_LIBRARY)
                .rawSubjects(List.of(SCIENCE_FICTION))
                .build();
            final SourceBook wikidata = titled(BookFieldSource.WIKIDATA)
                .rawSubjects(List.of(FANTASY))
                .build();

            final MergedBook merged = merger.merge(null, List.of(openLibrary, wikidata));

            assertThat(merged.subjectSources())
                .as("should list every source that contributed subjects")
                .containsExactlyInAnyOrder(BookFieldSource.OPEN_LIBRARY, BookFieldSource.WIKIDATA);
        }
    }

    @Nested
    @DisplayName("staged seed fallback")
    class StagedSeed {

        private static final String AUTHOR = Books.DUNE_AUTHOR;

        private static final String STALE_AUTHOR = "Valka";

        private static final String COMICS = "comics";

        @Test
        @DisplayName("a live source's authors win over the staged ones")
        void liveAuthorsWinOverStaged() {
            final SourceBook staged = stagedDune();
            final SourceBook openLibrary = titled(BookFieldSource.OPEN_LIBRARY)
                .authors(SourceAuthor.ofNames(List.of(AUTHOR)))
                .build();

            final MergedBook merged = merger.merge(staged, List.of(staged, openLibrary));

            assertThat(merged.book().authors())
                .as("should replace the staged authors with OpenLibrary's")
                .extracting(SourceAuthor::name)
                .containsExactly(AUTHOR);
        }

        @Test
        @DisplayName("a merge where only the staged seed resolves keeps everything but the series")
        void stagedOnlyMergeKeepsEverythingButSeries() {
            final SourceBook staged = stagedDune();

            final MergedBook merged = merger.merge(staged, List.of(staged));

            assertThat(merged.book()).satisfies(book -> {
                assertThat(book.title()).isEqualTo(TITLE);
                assertThat(book.authors())
                    .extracting(SourceAuthor::name)
                    .containsExactly(AUTHOR, STALE_AUTHOR);
                assertThat(book.rawSubjects()).containsExactly(COMICS);
                assertThat(book.seriesName())
                    .as("should drop the staged series when no live source confirms it")
                    .isNull();
            });
        }

        @Test
        @DisplayName("staged subjects yield to a live source's subjects")
        void stagedSubjectsYieldToLiveSubjects() {
            final SourceBook staged = stagedDune();
            final SourceBook openLibrary = titled(BookFieldSource.OPEN_LIBRARY)
                .rawSubjects(List.of(SCIENCE_FICTION))
                .build();

            final MergedBook merged = merger.merge(staged, List.of(staged, openLibrary));

            assertThat(merged.book().rawSubjects())
                .as("should leave out staged subjects when a live source has subjects")
                .containsExactly(SCIENCE_FICTION);
        }

        private static SourceBook stagedDune() {
            return titled(BookFieldSource.STAGED)
                .authors(SourceAuthor.ofNames(List.of(AUTHOR, STALE_AUTHOR)))
                .rawSubjects(List.of(COMICS))
                .seriesName(SERIES)
                .seriesPosition(WIKIDATA_VOLUME)
                .build();
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
            final SourceBook openLibrary = titled(BookFieldSource.OPEN_LIBRARY)
                .publicationYear(ORIGINAL_YEAR)
                .coverUrl(COVER_URL)
                .rawSubjects(List.of(FANTASY))
                .build();

            final MergedBook merged = merger.merge(null, List.of(google, openLibrary));

            assertThat(merged.provenanceOf(BookField.TITLE)).isEqualTo(BookFieldSource.GOOGLE_BOOKS);
            assertThat(merged.provenanceOf(BookField.PUBLICATION_YEAR)).isEqualTo(BookFieldSource.OPEN_LIBRARY);
            assertThat(merged.provenanceOf(BookField.COVER)).isEqualTo(BookFieldSource.OPEN_LIBRARY);
            assertThat(merged.provenanceOf(BookField.SUBJECTS)).isEqualTo(BookFieldSource.OPEN_LIBRARY);
        }

        @Test
        @DisplayName("every source identifier is carried onto the merged book")
        void identifiersAreCarried() {
            final SourceBook google = titled(BookFieldSource.GOOGLE_BOOKS)
                .googleBooksVolumeId(GOOGLE_ID)
                .isbn13(ISBN)
                .build();
            final SourceBook wikidata = titled(BookFieldSource.WIKIDATA)
                .wikidataQid(WIKIDATA_QID)
                .build();

            final MergedBook merged = merger.merge(null, List.of(google, wikidata));

            assertThat(merged.book().googleBooksVolumeId()).isEqualTo(GOOGLE_ID);
            assertThat(merged.book().wikidataQid()).isEqualTo(WIKIDATA_QID);
            assertThat(merged.book().isbn13()).isEqualTo(ISBN);
        }
    }
}
