package com.betterreads.text;

import static com.betterreads.testsupport.Books.FRENCH_BLURB;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * English prose passes on its share of common function words. Text in another language fails even
 * as long, fluent jacket copy, the shape Apple Books serves for foreign editions sold under an
 * English title.
 */
class EnglishTextTest {

    private static final String NOUN_HEAVY_FRONT_MATTER =
        "Collected essays, letters, notebooks, marginalia. Translated by Valka Onderra. "
        + "Introduction, chronology, textual notes, appendices, bibliography, index. Illustrated "
        + "throughout, including thirty-two colour plates and the author's own annotated maps of "
        + "the archipelago.";

    private static final String SPARSE_ANCHOR_ENGLISH =
        "These exceptional stories show that science fiction is no longer a field completely "
        + "reserved for men.";

    @Test
    @DisplayName("noun-heavy English front matter passes on its anchor words")
    void nounHeavyFrontMatterPasses() {
        assertThat(EnglishText.isEnglish(NOUN_HEAVY_FRONT_MATTER)).isTrue();
    }

    @Test
    @DisplayName("a short English sentence with few anchor words passes on its function-word share")
    void sparseAnchorEnglishPasses() {
        assertThat(EnglishText.isEnglish(SPARSE_ANCHOR_ENGLISH)).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("foreignBlurbs")
    @DisplayName("a foreign-language blurb fails")
    void foreignBlurbFails(final String language, final String blurb) {
        assertThat(EnglishText.isEnglish(blurb)).isFalse();
    }

    static Stream<Arguments> foreignBlurbs() {
        return Stream.of(
            Arguments.of("french", FRENCH_BLURB),
            Arguments.of("italian",
                "Torna l'autore dell'acclamata Licanius Trilogy con una storia ricca di intrighi "
                + "politici, accademie magiche, potere e sotterfugi. La Repubblica catenia governa "
                + "il mondo, ma non sa tutto."),
            Arguments.of("spanish",
                "Durante mil años cayó la ceniza y no florecieron las flores. Durante mil años los "
                + "skaa fueron esclavos en la miseria y vivieron con miedo. El Lord Legislador "
                + "reinó con poder absoluto gracias al terror y a su inmortalidad."),
            Arguments.of("german",
                "Seit tausend Jahren fällt die Asche vom Himmel und blühen keine Blumen mehr. Seit "
                + "tausend Jahren schuften die Skaa als Sklaven in Elend und Angst. Der Oberste "
                + "Herrscher regiert mit absoluter Macht über das Reich."));
    }

    @Test
    @DisplayName("a French blurb with a short English trailer fails")
    void frenchWithEnglishTrailerFails() {
        final String mixed = FRENCH_BLURB + " The first book of the Hierarchy series.";

        assertThat(EnglishText.isEnglish(mixed)).isFalse();
    }

    @Test
    @DisplayName("text with no letters fails")
    void textWithNoLettersFails() {
        assertThat(EnglishText.isEnglish("1984 -- 2001: 3, 4, 5.")).isFalse();
    }
}
