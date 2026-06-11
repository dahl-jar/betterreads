package com.betterreads.catalog.service.source;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DescriptionCleaner}. Source descriptions arrive with HTML tags, Markdown
 * emphasis, footnote references, and reference-style links that should not be stored. A clean blurb
 * is plain prose.
 */
class DescriptionCleanerTest {

    @Test
    @DisplayName("strips Markdown emphasis markers, keeping the words")
    void stripsEmphasisMarkers() {
        final String raw = "***Hercule Poirot*** is on board, and the killer **must** be too.";

        final String cleaned = DescriptionCleaner.clean(raw);

        assertThat(cleaned).isEqualTo("Hercule Poirot is on board, and the killer must be too.");
    }

    @Test
    @DisplayName("strips HTML tags and decodes common entities")
    void stripsHtmlAndEntities() {
        final String raw = "<p>A boy &amp; his fox escape the farmers&#39; traps.</p>";

        final String cleaned = DescriptionCleaner.clean(raw);

        assertThat(cleaned).isEqualTo("A boy & his fox escape the farmers' traps.");
    }

    @Test
    @DisplayName("a tag between sentences becomes a space, not a glued word")
    void separatesSentencesAtTags() {
        final String raw = "<p>Moiraine arrives in the Two Rivers!</p><p>The Eye of the World begins.</p>";

        final String cleaned = DescriptionCleaner.clean(raw);

        assertThat(cleaned).isEqualTo("Moiraine arrives in the Two Rivers! The Eye of the World begins.");
    }

    @Test
    @DisplayName("an inline tag leaves no space before the following punctuation")
    void leavesNoSpaceBeforePunctuationAfterInlineTags() {
        final String raw = "He reads <i>Dune</i>, then sleeps.";

        final String cleaned = DescriptionCleaner.clean(raw);

        assertThat(cleaned).isEqualTo("He reads Dune, then sleeps.");
    }

    @Test
    @DisplayName("decodes a numeric non-breaking-space entity to a plain space")
    void decodesNumericEntity() {
        final String raw = "Hadrian is lost.<br />&#xa0;<br />For half a century he has searched.";

        final String cleaned = DescriptionCleaner.clean(raw);

        assertThat(cleaned).isEqualTo("Hadrian is lost. For half a century he has searched.");
    }

    @Test
    @DisplayName("normalizes carriage returns and collapses blank runs into single breaks")
    void normalizesWhitespace() {
        final String raw = "First paragraph.\r\n\r\nSecond paragraph.";

        final String cleaned = DescriptionCleaner.clean(raw);

        assertThat(cleaned).isEqualTo("First paragraph.\n\nSecond paragraph.");
    }

    @Test
    @DisplayName("removes footnote references and reference-style link definitions")
    void removesFootnotesAndLinkDefinitions() {
        final String raw = "Frodo bears the Ring[1] to Mordor.\n\n  [1]: https://example.org/ring";

        final String cleaned = DescriptionCleaner.clean(raw);

        assertThat(cleaned).isEqualTo("Frodo bears the Ring to Mordor.");
    }

    @Test
    @DisplayName("unwraps an inline Markdown link to its text")
    void unwrapsInlineLink() {
        final String raw = "See [The Two Towers](https://openlibrary.org/works/OL27479W) next.";

        final String cleaned = DescriptionCleaner.clean(raw);

        assertThat(cleaned).isEqualTo("See The Two Towers next.");
    }

    @Test
    @DisplayName("leaves clean prose unchanged")
    void leavesCleanProseUnchanged() {
        final String raw = "Mr. Fox steals food from three brutish farmers to feed his family.";

        final String cleaned = DescriptionCleaner.clean(raw);

        assertThat(cleaned).isEqualTo(raw);
    }

    @Test
    @DisplayName("returns an empty string for blank input")
    void returnsEmptyForBlank() {
        assertThat(DescriptionCleaner.clean("   ")).isEmpty();
    }
}
