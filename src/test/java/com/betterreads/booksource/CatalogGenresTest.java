package com.betterreads.booksource;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * OpenLibrary mixes plot elements like {@code arkenstone} into its subjects. The fixtures are real
 * subjects from the live Hobbit (OL27482W) and Dune (OL893415W) works on 2026-05-28.
 */
class CatalogGenresTest {

    @Nested
    @DisplayName("real genre subjects are recognized")
    class Genres {

        private static final String GRAPHIC_NOVEL = "graphic novel";

        private static final Pattern GENRE_SEPARATOR = Pattern.compile("\\|");

        @ParameterizedTest(name = "\"{0}\" is {1}")
        @CsvSource({
            "fantasy, fantasy",
            "fantasy fiction, fiction|fantasy",
            "science fiction, science fiction",
            "Science Fiction, science fiction",
            "fiction, fiction",
            "juvenile fiction, fiction",
            "young adult fiction, fiction|young adult",
            "classics, classics",
            "mystery, mystery",
            "romance, romance",
            "horror, horror",
            "science/fiction, science fiction",
            "young_adult, young adult"
        })
        void recognizesGenres(final String subject, final String genres) {
            final List<String> expected = GENRE_SEPARATOR.splitAsStream(genres).toList();

            assertThat(CatalogGenres.extractGenres(subject)).containsExactlyElementsOf(expected);
        }

        @Test
        @DisplayName("should match a hyphenated genre (real OpenLibrary label)")
        void shouldMatchHyphenatedGenre() {
            assertThat(CatalogGenres.extractGenres("Science-fiction"))
                .as("OpenLibrary writes 'Science-fiction', which maps to 'science fiction' and never "
                    + "to bare 'fiction'")
                .containsExactly("science fiction");
        }

        @Test
        @DisplayName("should match a plural genre followed by more words")
        void shouldMatchPluralFollowedByMoreWords() {
            final Set<String> genres = CatalogGenres.extractGenres("Graphic novels collection");

            assertThat(genres).containsExactly(GRAPHIC_NOVEL);
        }
    }

    @Nested
    @DisplayName("plot-element noise is rejected")
    class Noise {

        @ParameterizedTest(name = "\"{0}\" is not a genre")
        @ValueSource(strings = {
            "thrushes",
            "arkenstone",
            "the one ring",
            "eagles",
            "giant spiders",
            "invisibility",
            "battle of five armies",
            "dune (imaginary place)",
            "fictional characters"
        })
        void rejectsPlotElements(final String subject) {
            assertThat(CatalogGenres.extractGenres(subject))
                .as("\"%s\" is a plot element, not a genre, and must be dropped", subject)
                .isEmpty();
        }

        @Test
        @DisplayName("should return no genres for a null subject")
        void shouldReturnNoGenresForNullSubject() {
            assertThat(CatalogGenres.extractGenres(null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("machine tags embedding a genre word are still rejected")
    class MachineTags {

        @ParameterizedTest(name = "\"{0}\" is a machine tag, not a genre")
        @ValueSource(strings = {
            "nyt:trade_fiction_paperback=2011-12-31",
            "series:Fantasy Classics",
            ":fiction",
            "=fiction"
        })
        void rejectsTagsThatEmbedGenreWords(final String subject) {
            assertThat(CatalogGenres.extractGenres(subject))
                .as("\"%s\" carries ':' or '=', so it is a machine tag even though a genre word "
                    + "appears inside it", subject)
                .isEmpty();
        }
    }

    @Nested
    @DisplayName("subject lists reduce to a canonical genre list")
    class ReduceToCanonical {

        @Test
        @DisplayName("a null subject list reduces to an empty list")
        void reducesNullListToEmpty() {
            final List<String> reduced = CatalogGenres.reduceToCanonical(null);

            assertThat(reduced).isEmpty();
        }

        @Test
        @DisplayName("variants of the same genre across subjects collapse to one term each")
        void collapsesVariantsToDistinctTerms() {
            final List<String> variants = List.of(
                "Fantasy", "American fantasy fiction", "English fantasy fiction");

            final List<String> reduced = CatalogGenres.reduceToCanonical(variants);

            assertThat(reduced).containsExactly("fantasy", "fiction");
        }
    }
}
