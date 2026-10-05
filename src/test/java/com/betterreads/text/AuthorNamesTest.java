package com.betterreads.text;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class AuthorNamesTest {

    private static final String TOLKIEN_KEY = "jrrtolkien";

    private static final String TOLKIEN_LAST_FIRST = "Tolkien, J.R.R.";

    @Nested
    class Key {

        @ParameterizedTest
        @ValueSource(strings = {"J.R.R. Tolkien", "J. R. R. Tolkien", "JRR Tolkien"})
        void shouldKeyInitialsSpacingAlike(final String name) {
            assertThat(AuthorNames.key(name)).isEqualTo(TOLKIEN_KEY);
        }

        @Test
        void shouldKeyLastFirstAsFirstLast() {
            assertThat(AuthorNames.key(TOLKIEN_LAST_FIRST)).isEqualTo(TOLKIEN_KEY);
        }

        @Test
        void shouldKeepSuffixAfterComma() {
            assertThat(AuthorNames.key("Smith, Jr.")).isEqualTo("smithjr");
        }

        @Test
        void shouldFoldCaseAndDiacritics() {
            assertThat(AuthorNames.key("JOSÉ SARAMAGO")).isEqualTo("josesaramago");
        }

        @ParameterizedTest
        @CsvSource({"Mœbius, moebius", "Torunn Grønbekk, torunngronbekk", "Yıldıray Çınar, yildiraycinar"})
        void shouldFoldLettersWithoutMarks(final String name, final String expected) {
            assertThat(AuthorNames.key(name)).isEqualTo(expected);
        }
    }

    @Nested
    class Split {

        @Test
        void shouldSplitSemicolonList() {
            assertThat(AuthorNames.split("Ray Snyder; Al Rio; Neil Nelson"))
                .containsExactly("Ray Snyder", "Al Rio", "Neil Nelson");
        }

        @Test
        void shouldSplitSlashList() {
            assertThat(AuthorNames.split("Kwitney, Alisa/ Jones, Joelle"))
                .isEqualTo(List.of("Kwitney, Alisa", "Jones, Joelle"));
        }

        @Test
        void shouldKeepAndInName() {
            final String couple = "Mary and Bryan Talbot";

            assertThat(AuthorNames.split(couple)).containsExactly(couple);
        }
    }

    @Nested
    class Display {

        @ParameterizedTest
        @CsvSource({
            "STEPHEN KING, Stephen King",
            "ed mcbain, Ed McBain",
            "PATRICK O'BRIAN, Patrick O'Brian",
            "VINCENT VAN GOGH, Vincent van Gogh",
            "DE LA CRUZ, De la Cruz",
            "JAMES MC, James Mc"
        })
        void shouldTitleCaseAllCaps(final String stored, final String expected) {
            assertThat(AuthorNames.display(stored)).isEqualTo(expected);
        }

        @Test
        void shouldShowLastFirstAsFirstLast() {
            assertThat(AuthorNames.display(TOLKIEN_LAST_FIRST)).isEqualTo("J.R.R. Tolkien");
        }

        @Test
        void shouldCollapseSpaces() {
            assertThat(AuthorNames.collapseSpaces("  Stephen              Jones ")).isEqualTo("Stephen Jones");
        }

        @Test
        void shouldKeepMixedCaseName() {
            final String name = "bell hooks Jr";

            assertThat(AuthorNames.display(name)).isEqualTo(name);
        }
    }

    @Nested
    class Surname {

        @ParameterizedTest
        @CsvSource({
            "George R. R. Martin, Martin",
            "'Sanderson, Brandon', Sanderson",
            "Martin Luther King Jr., King",
            "James Tynion IV, Tynion"
        })
        void shouldTakeFamilyName(final String name, final String expected) {
            assertThat(AuthorNames.surname(name)).isEqualTo(expected);
        }

        @Test
        void shouldUseSingleNameAsSurname() {
            final String name = "Moebius";

            assertThat(AuthorNames.surname(name)).isEqualTo(name);
        }
    }
}
