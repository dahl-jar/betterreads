package com.betterreads.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TextMatchTest {

    private static final String DUNE = "Dune";

    private static final String DUNE_MESSIAH = "Dune Messiah";

    private static final String FULLMETAL = "Fullmetal Alchemist, Vol. 1";

    private static final String CHRONOLOGY = "The New Essential Chronology";

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
            arguments("???", DUNE),
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
            arguments("Eat, Pray, Love, Vol. 1", "Eat Pray Love Volume 1"),
            arguments("An Ember in the Ashes", "Ember in the Ashes"),
            arguments("A Flame in Byzantium", "Flame in Byzantium"),
            arguments("Ember in the Ashes, An", "An Ember in the Ashes: A Novel"),
            arguments("Flame in Byzantium, A", "A Flame in Byzantium: A Novel"),
            arguments("The De\uFB01nitive Guide", "The Definitive Guide"));
    }

    @ParameterizedTest(name = "{0} with {1}")
    @MethodSource("sameBookTitles")
    void shouldMatchSameBookTitles(final String seed, final String hit) {
        assertThat(TextMatch.sameBookTitle(seed, hit)).isTrue();
        assertThat(TextMatch.sameBookTitle(hit, seed)).isTrue();
    }

    static Stream<Arguments> differentBookTitles() {
        return Stream.of(
            arguments(DUNE, "The Art of Dune"),
            arguments(DUNE, DUNE_MESSIAH),
            arguments("Star Wars: Heir to the Empire", "Star Wars: Thrawn"),
            arguments(DUNE, "Neuromancer"),
            arguments("Le Petit Prince", "The Little Prince"),
            arguments(FULLMETAL, "Fullmetal Alchemist, Vol. 2"),
            arguments("Бvol", "Vol"),
            arguments("Spider-Man", "Man"),
            arguments("Red Volcano", "Red"));
    }

    @ParameterizedTest(name = "{0} with {1}")
    @MethodSource("differentBookTitles")
    void shouldNotMatchDifferentBookTitles(final String seed, final String hit) {
        assertThat(TextMatch.sameBookTitle(seed, hit)).isFalse();
        assertThat(TextMatch.sameBookTitle(hit, seed)).isFalse();
    }
}
