package com.betterreads.bookdescription;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Source descriptions arrive with HTML tags and Markdown markup that should not be stored. */
class DescriptionCleanerTest {

    @Test
    @DisplayName("strips Markdown emphasis markers, keeping the words")
    void stripsEmphasisMarkers() {
        final String raw = "***Hercule Poirot*** is on board, and the killer **must** be too.";

        final String cleaned = DescriptionCleaner.clean(raw);

        assertThat(cleaned).isEqualTo("Hercule Poirot is on board, and the killer must be too.");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("markup")
    void shouldRemoveMarkup(final String name, final String raw, final String expected) {
        final String cleaned = DescriptionCleaner.clean(raw);

        assertThat(cleaned).isEqualTo(expected);
    }

    static Stream<Arguments> markup() {
        return Stream.of(
            Arguments.of("html tag", "<p>A boy and his fox.</p>", "A boy and his fox."),
            Arguments.of("named entity", "A boy &amp; his fox.", "A boy & his fox."),
            Arguments.of("decimal entity", "The farmers&#39; traps.", "The farmers' traps."),
            Arguments.of("footnote reference", "Frodo bears the Ring[1] to Mordor.",
                "Frodo bears the Ring to Mordor."),
            Arguments.of("link definition", "Frodo reaches Mordor.\n\n  [1]: https://example.org/ring",
                "Frodo reaches Mordor."),
            Arguments.of("reference link", "Read [The Two Towers][2] next.", "Read The Two Towers next."),
            Arguments.of("list bullet", "- Darrow of Lykos", "Darrow of Lykos"),
            Arguments.of("thematic break", "Darrow.\n----\nSevro.", "Darrow.\n\nSevro."),
            Arguments.of("carriage returns", "Mustang.\r\n\r\nCassius.", "Mustang.\n\nCassius."),
            Arguments.of("blank run", "Hadrian.\n\n\n\nValka.", "Hadrian.\n\nValka."));
    }

    @Test
    @DisplayName("a tag between sentences becomes a space")
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
        final String raw = "Hadrian&#xa0;Marlowe is lost.";

        final String cleaned = DescriptionCleaner.clean(raw);

        assertThat(cleaned).isEqualTo("Hadrian Marlowe is lost.");
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
}
