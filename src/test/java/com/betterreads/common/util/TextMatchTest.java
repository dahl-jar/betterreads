package com.betterreads.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TextMatch#canonicalTitleMatches}. Two titles match when their core titles are
 * equal once case, a trailing subtitle, and surrounding punctuation are removed. A title that merely
 * shares a leading word with another, like a sequel, does not match.
 */
class TextMatchTest {

    private static final String DUNE = "Dune";

    @Test
    @DisplayName("identical titles match")
    void identicalTitlesMatch() {
        assertThat(TextMatch.canonicalTitleMatches(DUNE, DUNE)).isTrue();
    }

    @Test
    @DisplayName("case and surrounding whitespace are ignored")
    void ignoresCaseAndWhitespace() {
        assertThat(TextMatch.canonicalTitleMatches("  dune ", "DUNE")).isTrue();
    }

    @Test
    @DisplayName("a trailing colon subtitle on one side is ignored")
    void ignoresColonSubtitle() {
        assertThat(TextMatch.canonicalTitleMatches("Dune: A Novel", DUNE)).isTrue();
    }

    @Test
    @DisplayName("a trailing parenthetical qualifier on one side is ignored")
    void ignoresParentheticalQualifier() {
        assertThat(TextMatch.canonicalTitleMatches(DUNE, "Dune (novel)")).isTrue();
    }

    @Test
    @DisplayName("a sequel sharing the leading word does not match")
    void sequelDoesNotMatch() {
        assertThat(TextMatch.canonicalTitleMatches("Dune Messiah", DUNE)).isFalse();
    }

    @Test
    @DisplayName("a different book whose title contains the other does not match")
    void containingTitleDoesNotMatch() {
        assertThat(TextMatch.canonicalTitleMatches(DUNE, "Children of Dune")).isFalse();
    }
}
