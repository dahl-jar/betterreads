package com.betterreads.catalog.service.source;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DescriptionQuality}. A usable blurb is plain prose of a sensible length with
 * no catalog boilerplate or wiki-dump structure. The assessment cleans the text, rejects the
 * unusable, and scores the rest.
 */
class DescriptionQualityTest {

    private static final String BOLD = "***";

    private static final String GOOD =
        "Mr. Fox steals food from three brutish farmers to feed his family. Tired of being "
        + "outwitted, the farmers set out to dig him from his den, and the foxes must outlast them.";

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
}
