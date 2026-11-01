package com.betterreads.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class TextMatchTest {

    private static final String DUNE = "Dune";

    private static final String DUNE_MESSIAH = "Dune Messiah";

    private static final String FULLMETAL = "Fullmetal Alchemist, Vol. 1";

    private static final String CHRONOLOGY = "The New Essential Chronology";

    private static final String SAGA = "Saga, Vol. 1";

    private static final String NEUROMANCER = "Neuromancer";

    private static final String SAGA_VOLUME = "Saga, Volume ";

    private static final String EAT_PRAY_LOVE = "Eat, Pray, Love, Vol. 1";

    private static final String NO_WORDS = "???";

    @Test
    @DisplayName("case is ignored")
    void shouldIgnoreCase() {
        assertThat(TextMatch.canonicalTitleMatches("dune", "DUNE")).isTrue();
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
        assertThat(TextMatch.canonicalTitleMatches(DUNE_MESSIAH, DUNE)).isFalse();
    }

    @Test
    @DisplayName("a different book whose title contains the other does not match")
    void containingTitleDoesNotMatch() {
        assertThat(TextMatch.canonicalTitleMatches(DUNE, "Children of Dune")).isFalse();
    }

    @Test
    void shouldKeepTrademarkLettersInFold() {
        assertThat(TextMatch.fold("Prince\u2122")).isEqualTo("princetm");
    }

    static Stream<Arguments> sameBookTitles() {
        return Stream.of(
            arguments(DUNE, "Dune: Deluxe Edition"),
            arguments("The Martian", "Martian"),
            arguments("Debris and Detritus", "Debris & Detritus"),
            arguments("Cthulhu's Reign", "Cthulhus Reign"),
            arguments("Wrath of Betty", "Willful Child: Wrath of Betty"),
            arguments("Witch & Curse (Wicked)", "Wicked: Witch & Curse"),
            arguments(FULLMETAL, "Fullmetal Alchemist"),
            arguments(FULLMETAL, "Fullmetal Alchemist, Volume 1"),
            arguments(FULLMETAL, "Fullmetal Alchemist Vol. 1: The Land of Sand"),
            arguments("Eat, Pray, Love", "Eat Pray Love"),
            arguments("Thérèse Raquin", "Therese Raquin"),
            arguments("Æon Flux", "Aeon Flux"),
            arguments("Martian, The", "The Martian: A Novel"),
            arguments("Demon Knights #1", "Demon knights"),
            arguments("Fathomless Riches ; or How I Went From Pop to Pulpit", "Fathomless Riches"),
            arguments("Κρίτων / Ἀπολογία", "Κρίτων"),
            arguments("Dune [Deluxe Edition]", DUNE),
            arguments("[Deluxe Edition] Dune", DUNE),
            arguments("Wicked: The Witch", "Witch"),
            arguments("Star Wars - The New Essential Chronology", CHRONOLOGY),
            arguments("Star Wars \u2013 The New Essential Chronology", CHRONOLOGY),
            arguments("Star Wars \u2014 The New Essential Chronology", CHRONOLOGY),
            arguments("Batman--Riddler", "Riddler"),
            arguments("Dune (Deluxe", DUNE),
            arguments(EAT_PRAY_LOVE, "Eat Pray Love Volume 1"),
            arguments("An Ember in the Ashes", "Ember in the Ashes"),
            arguments("A Flame in Byzantium", "Flame in Byzantium"),
            arguments("Ember in the Ashes, An", "An Ember in the Ashes: A Novel"),
            arguments("Flame in Byzantium, A", "A Flame in Byzantium: A Novel"),
            arguments("The De\uFB01nitive Guide", "The Definitive Guide"),
            arguments(SAGA, "Saga, Volume One"),
            arguments(SAGA, "Saga, Vol. 01"),
            arguments(SAGA, "Saga, Vol. I"),
            arguments(EAT_PRAY_LOVE, "Eat Pray Love Volume One"),
            arguments("Saga Vol. 01: Chapter One", "Saga, Volume 1"),
            arguments(DUNE, "Dune\u2014A Novel"),
            arguments(DUNE, "Dune\u2013A Novel"),
            arguments(DUNE, "Dune\u2122"));
    }

    @ParameterizedTest(name = "{0} with {1}")
    @MethodSource("sameBookTitles")
    void shouldMatchSameBookTitles(final String seed, final String hit) {
        assertThat(TextMatch.sameBookTitle(seed, hit)).isTrue();
        assertThat(TextMatch.sameBookTitle(hit, seed)).isTrue();
    }

    @ParameterizedTest(name = "{0}, {1}, {2}")
    @CsvSource({
        "1, One, I",
        "2, Two, II",
        "3, Three, III",
        "4, Four, IV",
        "5, Five, V",
        "6, Six, VI",
        "7, Seven, VII",
        "8, Eight, VIII",
        "9, Nine, IX",
        "10, Ten, X",
        "11, Eleven, XI",
        "12, Twelve, XII"
    })
    void shouldMatchVolumeNumberForms(final String digits, final String word, final String roman) {
        final String volume = SAGA_VOLUME + digits;

        assertThat(TextMatch.sameBookTitle(volume, SAGA_VOLUME + word)).isTrue();
        assertThat(TextMatch.sameBookTitle(volume, SAGA_VOLUME + roman)).isTrue();
    }

    static Stream<Arguments> differentBookTitles() {
        return Stream.of(
            arguments(DUNE, "The Art of Dune"),
            arguments(DUNE, DUNE_MESSIAH),
            arguments("Star Wars: Heir to the Empire", "Star Wars: Thrawn"),
            arguments(DUNE, NEUROMANCER),
            arguments("Le Petit Prince", "The Little Prince"),
            arguments(FULLMETAL, "Fullmetal Alchemist, Vol. 2"),
            arguments("Бvol", "Vol"),
            arguments("Spider-Man", "Man"),
            arguments("Red Volcano", "Red"),
            arguments(SAGA, "Saga, Vol. 2"),
            arguments(SAGA, "Saga, Volume Two"),
            arguments(SAGA, "Saga, Vol. 10"),
            arguments("Saga, Vol. 0", "Saga, Vol."),
            arguments("Diaries, 1969\u20131976", "1976"),
            arguments("Area 51\u2014The Story", "The Story"),
            arguments("Book\u20141984", "1984"),
            arguments(NEUROMANCER, NO_WORDS),
            arguments(NEUROMANCER, "(Dune Messiah)"),
            arguments("The", NEUROMANCER),
            arguments(NO_WORDS, NO_WORDS));
    }

    @ParameterizedTest(name = "{0} with {1}")
    @MethodSource("differentBookTitles")
    void shouldNotMatchDifferentBookTitles(final String seed, final String hit) {
        assertThat(TextMatch.sameBookTitle(seed, hit)).isFalse();
        assertThat(TextMatch.sameBookTitle(hit, seed)).isFalse();
    }
}
