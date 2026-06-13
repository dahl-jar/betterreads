package com.betterreads.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Unit tests for {@link EnglishText#isEnglish}. English prose passes on its share of common
 * function words; text in another language fails even when it is long, fluent jacket copy, the
 * shapes Apple Books serves for foreign editions sold under an English title.
 */
class EnglishTextTest {

    private static final String ENGLISH_BLURB =
        "Expected by his enemies to die the miserable death of a military slave, Kaladin survived "
        + "to be given command of the royal bodyguards, a controversial first for a low-status "
        + "darkeyes. Now he must protect the king and Dalinar from every common peril.";

    private static final String LATIN_EPIGRAPH_LEAD =
        "AUDI. VIDE. TACE. The Catenan Republic, the Hierarchy, may rule the world now, but they "
        + "do not know everything. I tell them my name is Vis Telimus. I tell them I was orphaned "
        + "after a tragic accident three years ago, and that good fortune alone has led to my "
        + "acceptance into their most prestigious school.";

    private static final String SPARSE_ANCHOR_ENGLISH =
        "These exceptional stories show that science fiction is no longer a field completely "
        + "reserved for men.";

    private static final String FRENCH_BLURB =
        "Ils m'appellent Vis Telimus. Ils croient que j'ai eu la chance d'être adopté par un "
        + "sénateur et envoyé à l'Académie pour rejoindre l'élite. Ainsi la Hiérarchie a-t-elle "
        + "conquis le monde. Mais la vérité, c'est que je suis venu résoudre un meurtre.";

    @Test
    @DisplayName("an English plot blurb passes")
    void englishBlurbPasses() {
        assertThat(EnglishText.isEnglish(ENGLISH_BLURB)).isTrue();
    }

    @Test
    @DisplayName("an English blurb opening with a Latin epigraph passes")
    void latinEpigraphLeadPasses() {
        assertThat(EnglishText.isEnglish(LATIN_EPIGRAPH_LEAD)).isTrue();
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
