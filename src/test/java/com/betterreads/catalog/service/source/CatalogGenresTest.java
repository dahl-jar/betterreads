package com.betterreads.catalog.service.source;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for the genre allowlist that separates real catalog genres from the plot-element noise
 * OpenLibrary mixes into its subject array.
 *
 * <p>The kept/dropped fixtures are the real subjects observed on the live Hobbit (OL27482W) and
 * Dune (OL893415W) works on 2026-05-28. The point of the allowlist is that {@code thrushes},
 * {@code arkenstone}, and {@code the one ring} are plot elements, not genres, and must never reach
 * the catalog as shelf categories, while {@code fantasy fiction} and {@code science fiction} must.
 */
class CatalogGenresTest {

    @Nested
    @DisplayName("real genre subjects are recognized")
    class Genres {

        @ParameterizedTest(name = "\"{0}\" is a genre")
        @ValueSource(strings = {
            "fantasy",
            "fantasy fiction",
            "science fiction",
            "fiction",
            "juvenile fiction",
            "young adult fiction",
            "classics",
            "mystery",
            "romance",
            "horror"
        })
        void recognizesGenres(final String subject) {
            assertThat(CatalogGenres.isGenre(subject))
                .as("\"%s\" is a real shelf genre and must be kept", subject)
                .isTrue();
        }

        @Test
        @DisplayName("genre match is case-insensitive (subjects arrive in mixed case from OpenLibrary)")
        void matchIsCaseInsensitive() {
            assertThat(CatalogGenres.isGenre("Science Fiction")).isTrue();
        }

        @Test
        @DisplayName("hyphen and plural variants of a genre still match (real OpenLibrary labels)")
        void hyphenAndPluralVariantsMatch() {
            assertThat(CatalogGenres.extractGenres("Science-fiction"))
                .as("OpenLibrary uses the hyphenated 'Science-fiction'; it must map to the canonical "
                    + "'science fiction', not fall through to bare 'fiction'")
                .containsExactly("science fiction");
            assertThat(CatalogGenres.extractGenres("Graphic novels"))
                .as("the plural 'Graphic novels' must map to the canonical 'graphic novel'")
                .contains("graphic novel");
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
            "dune (imaginary place)"
        })
        void rejectsPlotElements(final String subject) {
            assertThat(CatalogGenres.isGenre(subject))
                .as("\"%s\" is a plot element, not a genre, and must be dropped", subject)
                .isFalse();
        }

        @Test
        @DisplayName("null and blank are not genres")
        void nullAndBlankRejected() {
            assertThat(CatalogGenres.isGenre(null)).isFalse();
            assertThat(CatalogGenres.isGenre("  ")).isFalse();
        }
    }

    @Nested
    @DisplayName("machine tags embedding a genre word are still rejected")
    class MachineTags {

        @ParameterizedTest(name = "\"{0}\" is a machine tag, not a genre")
        @ValueSource(strings = {
            "nyt:trade_fiction_paperback=2011-12-31",
            "nyt:trade-fiction-paperback=2021-11-07",
            "nyt:combined-print-and-e-book-nonfiction=2018-03-11",
            "award:nebula_award=novel",
            "award:hugo_award=1966",
            "series:A Song of Ice and Fire"
        })
        void rejectsTagsThatEmbedGenreWords(final String subject) {
            assertThat(CatalogGenres.isGenre(subject))
                .as("\"%s\" contains 'fiction'/'nonfiction' as a substring but is a machine tag "
                    + "(has ':' or '='); the seed showed these leak past a naive substring match",
                    subject)
                .isFalse();
        }

        @Test
        @DisplayName("a parenthetical plot annotation with no genre token is rejected")
        void rejectsParentheticalPlotAnnotation() {
            assertThat(CatalogGenres.isGenre("seven kingdoms (imaginary place)")).isFalse();
            assertThat(CatalogGenres.isGenre("rand al'thor (fictitious character)")).isFalse();
        }
    }
}
