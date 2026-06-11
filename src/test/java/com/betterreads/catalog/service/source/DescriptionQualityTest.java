package com.betterreads.catalog.service.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Unit tests for {@link DescriptionQuality}. A usable blurb is plain prose of a sensible length with
 * no catalog boilerplate or wiki-dump structure. The assessment cleans the text, rejects the
 * unusable, and scores the rest on the sentences that describe the story.
 */
class DescriptionQualityTest {

    private static final String BOLD = "***";

    private static final int CEILING = 2400;

    private static final int PAST_CEILING_BLURBS = 20;

    private static final int PAST_CEILING_WORDS = 150;

    private static final String GOOD =
        "Mr. Fox steals food from three brutish farmers to feed his family. Tired of being "
        + "outwitted, the farmers set out to dig him from his den, and the foxes must outlast them.";

    @Nested
    @DisplayName("accepting and cleaning")
    class AcceptingAndCleaning {

        @Test
        @DisplayName("a clean prose blurb of sensible length is accepted and carries its cleaned text")
        void acceptsCleanProse() {
            final DescriptionQuality.Assessment assessment = DescriptionQuality.assess(GOOD);

            assertThat(assessment.usable()).isTrue();
            assertThat(assessment.cleaned()).isEqualTo(GOOD);
        }

        @Test
        @DisplayName("emphasis markup is cleaned before the text is judged")
        void cleansBeforeJudging() {
            final DescriptionQuality.Assessment assessment = DescriptionQuality.assess(BOLD + GOOD + BOLD);

            assertThat(assessment.usable()).isTrue();
            assertThat(assessment.cleaned()).isEqualTo(GOOD);
        }

        @Test
        @DisplayName("an over-long blurb is cut at a sentence end under the ceiling")
        void cutsAnOverlongBlurbAtASentenceEnd() {
            final String overlong = (GOOD + " ").repeat(PAST_CEILING_BLURBS).strip();

            final DescriptionQuality.Assessment assessment = DescriptionQuality.assess(overlong);

            assertThat(assessment.usable()).isTrue();
            assertThat(assessment.cleaned().length()).isLessThanOrEqualTo(CEILING);
            assertThat(assessment.cleaned())
                .as("the cut lands on a sentence end, not mid-sentence")
                .endsWith(".");
        }
    }

    @Nested
    @DisplayName("rejecting")
    class Rejecting {

        @Test
        @DisplayName("a too-short stub is rejected")
        void rejectsTooShort() {
            final DescriptionQuality.Assessment assessment = DescriptionQuality.assess("A short blurb.");

            assertThat(assessment.usable()).isFalse();
        }

        @Test
        @DisplayName("a library-catalog boilerplate prefix is rejected")
        void rejectsBoilerplatePrefix() {
            final String raw = "For use in schools and libraries only. On the planet of Arrakis, the "
                + "long-foretold time has come for the children of Dune to claim their destiny.";

            final DescriptionQuality.Assessment assessment = DescriptionQuality.assess(raw);

            assertThat(assessment.usable()).isFalse();
        }

        @Test
        @DisplayName("a wiki dump with a heading and a bulleted edition list is rejected")
        void rejectsWikiDump() {
            final String raw = "Tolkien's epic ushered in a new age of adventure.\n\n----------\n\n"
                + "**Contains**\n\n - [The Fellowship of the Ring][1]\n - [The Two Towers][2]\n"
                + " - [The Return of the King][3]\n\n  [1]: https://openlibrary.org/works/OL1W";

            final DescriptionQuality.Assessment assessment = DescriptionQuality.assess(raw);

            assertThat(assessment.usable()).isFalse();
        }

        @Test
        @DisplayName("an 'Also contained in' trailer with one linked bullet is rejected")
        void rejectsAlsoContainedInTrailer() {
            final String raw = GOOD + "\n\n----------\n**Also contained in:**\n\n"
                + "- [The Lord of the Rings][1]\n\n[1]: https://openlibrary.org/works/OL27448W";

            final DescriptionQuality.Assessment assessment = DescriptionQuality.assess(raw);

            assertThat(assessment.usable()).isFalse();
        }

        @Test
        @DisplayName("a 'Preceded by / Followed by' series trailer with reference links is rejected")
        void rejectsSeriesNavigationTrailer() {
            final String raw = GOOD + "\n\nPreceded by: [A Clash of Kings][1]\n"
                + "Followed by: [A Feast for Crows][2]\n\n([Source][3])\n\n"
                + "[1]: https://openlibrary.org/works/OL257939W\n"
                + "[2]: https://openlibrary.org/works/OL257948W\n"
                + "[3]: https://georgerrmartin.com/grrm_book/a-storm-of-swords";

            final DescriptionQuality.Assessment assessment = DescriptionQuality.assess(raw);

            assertThat(assessment.usable()).isFalse();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("publicationFactsTexts")
        @DisplayName("a description of only publication facts is rejected")
        void rejectsPublicationFactsOnly(final String name, final String raw) {
            final DescriptionQuality.Assessment assessment = DescriptionQuality.assess(raw);

            assertThat(assessment.usable())
                .as("every sentence is about the publication, none about the story")
                .isFalse();
        }

        static Stream<Arguments> publicationFactsTexts() {
            return Stream.of(
                Arguments.of("twok wikipedia lead",
                    "The Way of Kings is an epic fantasy novel written by American author "
                    + "Brandon Sanderson and the first book in The Stormlight Archive series. The "
                    + "novel was published on August 31, 2010, by Tor Books. The Way of Kings "
                    + "consists of one prelude, one prologue, 75 chapters, an epilogue, and nine "
                    + "interludes. It was followed by Words of Radiance in 2014. A leatherbound "
                    + "edition was released in 2021."),
                Arguments.of("series-navigation lead",
                    "To Play the Fool is the second book in the Kate Martinelli series by "
                    + "Laurie R. King. Preceded by A Grave Talent and followed by the novel With "
                    + "Child, it describes the investigation into the murder of a homeless man."),
                Arguments.of("publication-and-reception lead",
                    "The Eye of the World is a high fantasy novel by American writer Robert "
                    + "Jordan and the first book in The Wheel of Time series. Published by Tor "
                    + "Books on January 15, 1990, it was initially released as a large paperback. "
                    + "The original unabridged audiobook is narrated by Michael Kramer and Kate "
                    + "Reading. The initial publication of The Eye of the World included a "
                    + "prologue and 53 chapters. The book achieved both critical and commercial "
                    + "success. Critics lauded its tone and themes, while its similarities to The "
                    + "Lord of the Rings received both praise and criticism."));
        }

        @Test
        @DisplayName("an over-long run with no sentence end is rejected")
        void rejectsAnOverlongRunWithNoSentenceEnd() {
            final String unbroken = "wordswithoutanyend ".repeat(PAST_CEILING_WORDS).strip();

            final DescriptionQuality.Assessment assessment = DescriptionQuality.assess(unbroken);

            assertThat(assessment.usable()).isFalse();
        }
    }

    @Nested
    @DisplayName("scoring")
    class Scoring {

        @Test
        @DisplayName("the longer of two clean blurbs scores higher")
        void scoresLongerCleanBlurbHigher() {
            final String shorter = "Mr. Fox outwits three farmers to feed his family every night.";

            final int longScore = DescriptionQuality.assess(GOOD).score();
            final int shortScore = DescriptionQuality.assess(shorter).score();

            assertThat(longScore).isGreaterThan(shortScore);
        }

        @Test
        @DisplayName("an over-long blurb past the ceiling does not outscore a sensible one")
        void doesNotRewardLengthPastTheCeiling() {
            final String sensible = GOOD;
            final String bloated = GOOD + " " + "Filler sentence to pad the length. ".repeat(60);

            final int sensibleScore = DescriptionQuality.assess(sensible).score();
            final int bloatedScore = DescriptionQuality.assess(bloated).score();

            assertThat(sensibleScore).isGreaterThanOrEqualTo(bloatedScore);
        }

        @Test
        @DisplayName("publication-fact sentences add nothing to the score")
        void factSentencesAddNothingToTheScore() {
            final String withFacts = GOOD + " First published in 1990 by Tor Books.";

            final int plotScore = DescriptionQuality.assess(GOOD).score();
            final int withFactsScore = DescriptionQuality.assess(withFacts).score();

            assertThat(withFactsScore).isEqualTo(plotScore);
        }
    }
}
