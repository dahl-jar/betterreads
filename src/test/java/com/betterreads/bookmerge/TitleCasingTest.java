package com.betterreads.bookmerge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class TitleCasingTest {

    private static final String ENGLISH = "en";

    private static final String FRENCH = "fr";

    private static final String NORTHWEST_ANGLE = "Northwest Angle";

    @Test
    void shouldTakeAnotherSourcesCapitalizationOfTheSameTitle() {
        final String title = TitleCasing.capitalize(
            "Northwest angle", List.of("Northwest Angle: A Novel", "NORTHWEST angle", NORTHWEST_ANGLE), FRENCH);

        assertThat(title).isEqualTo(NORTHWEST_ANGLE);
    }

    @Test
    void shouldKeepTheTitleWhenNoSourceCapitalizesMoreWords() {
        final String title = TitleCasing.capitalize(NORTHWEST_ANGLE, List.of("Northwest ANGLE"), FRENCH);

        assertThat(title).isEqualTo(NORTHWEST_ANGLE);
    }

    @Test
    void shouldPreferSourceCapitalizationOverTitleCasing() {
        final String sourceSpelling = "Gone, But Not Forgotten";

        final String title = TitleCasing.capitalize("Gone, but not forgotten", List.of(sourceSpelling), ENGLISH);

        assertThat(title).isEqualTo(sourceSpelling);
    }

    @ParameterizedTest(name = "\"{0}\" becomes \"{1}\"")
    @CsvSource(delimiter = '|', value = {
        "Fish out of water | Fish Out of Water",
        "Sleeping with the fishes | Sleeping with the Fishes",
        "a tale worth fighting for | A Tale Worth Fighting For",
        "Fear of, and for, the dark | Fear of, and for, the Dark",
        "The night  is watching | The Night  Is Watching",
        "how I went from pop to pulpit | How I Went From Pop to Pulpit",
        "batman: the long halloween | Batman: The Long Halloween"
    })
    void shouldTitleCaseLowercaseEnglishTitle(final String lowercase, final String expected) {
        assertThat(TitleCasing.capitalize(lowercase, List.of(), ENGLISH)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "\"{0}\" is left unchanged")
    @CsvSource({"iPhone for seniors", "a history of Iran"})
    void shouldKeepDeliberateCasing(final String title) {
        assertThat(TitleCasing.capitalize(title, List.of(), ENGLISH)).isEqualTo(title);
    }

    @Test
    void shouldKeepNonEnglishTitle() {
        final String title = "La nuit des hiboux";

        assertThat(TitleCasing.capitalize(title, List.of(), FRENCH)).isEqualTo(title);
    }

    @Test
    void shouldIgnoreAllCapsAlternative() {
        final String title = "Return of the King";

        assertThat(TitleCasing.capitalize(title, List.of("RETURN OF THE KING"), FRENCH)).isEqualTo(title);
    }

    @Nested
    class AllCaps {

        private static final String LES_MISERABLES = "LES MISÉRABLES";

        @ParameterizedTest
        @ValueSource(strings = {"RETURN OF THE KING", "STAR TREK 3", "1984 V", "ВОЙНА И МИР", LES_MISERABLES})
        void shouldDetectAllCapsTitle(final String title) {
            assertThat(TitleCasing.isAllCaps(title)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "IT", "JLA", " JLA", "NOS4A2", "1984", "1984 2001", "V", "S.", "1Q84", "The Hobbit", "Война и мир",
            "新世界 世界", "STRAßE MITTE"
        })
        void shouldNotTreatTitleAsAllCaps(final String title) {
            assertThat(TitleCasing.isAllCaps(title)).isFalse();
        }

        @ParameterizedTest(name = "\"{0}\" becomes \"{1}\"")
        @CsvSource(delimiter = '|', quoteCharacter = '`', value = {
            "RETURN OF THE KING | Return of the King",
            "BATMAN: THE LONG HALLOWEEN | Batman: The Long Halloween",
            "WHAT DREAMS ARE MADE OF | What Dreams Are Made Of",
            "LES MISÉRABLES | Les Misérables",
            "V FOR VENDETTA | V for Vendetta",
            "WHY I WRITE | Why I Write",
            "STAR TREK 3 | Star Trek 3",
            "'SALEM'S LOT REVISITED | 'Salem's Lot Revisited",
            "(THE) LONG WALK | (The) Long Walk",
            "20TH CENTURY BOYS | 20th Century Boys",
            "1Q84 VOLUME 2 | 1q84 Volume 2",
            "BATMAN - THE LONG HALLOWEEN | Batman - The Long Halloween",
            "WAR -- AND PEACE | War -- And Peace",
            "BATMAN \u2013 THE LONG HALLOWEEN | Batman \u2013 The Long Halloween",
            "BATMAN \u2014 THE LONG HALLOWEEN | Batman \u2014 The Long Halloween",
            "WHO GOES THERE? A NOVEL | Who Goes There? A Novel",
            "RUN! A NOVEL | Run! A Novel"
        })
        void shouldFixCaseOfAllCapsEnglishTitle(final String title, final String expected) {
            assertThat(TitleCasing.fixAllCaps(title, ENGLISH)).isEqualTo(expected);
        }

        @Test
        void shouldKeepTitleThatIsNotAllCaps() {
            final String title = "iPhone for seniors";

            assertThat(TitleCasing.fixAllCaps(title, ENGLISH)).isEqualTo(title);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = FRENCH)
        void shouldKeepAllCapsTitleOutsideEnglish(final @Nullable String language) {
            assertThat(TitleCasing.fixAllCaps(LES_MISERABLES, language)).isEqualTo(LES_MISERABLES);
        }

        @ParameterizedTest(name = "\"{0}\" becomes \"{1}\"")
        @CsvSource(delimiter = '|', value = {
            "H.P. LOVECRAFT | H.P. Lovecraft",
            "B.P.R.D.: 1948 | B.P.R.D.: 1948",
            ".NET FOR BEGINNERS | .NET for Beginners"
        })
        void shouldKeepInitialsInCapitals(final String title, final String expected) {
            assertThat(TitleCasing.fixAllCaps(title, ENGLISH)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "\"{0}\" becomes \"{1}\"")
        @CsvSource(delimiter = '|', value = {
            "MAYFLOWER II | Mayflower II",
            "NECROSCOPE III: THE SOURCE | Necroscope III: The Source"
        })
        void shouldKeepRomanNumeralInCapitals(final String title, final String expected) {
            assertThat(TitleCasing.fixAllCaps(title, ENGLISH)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "\"{0}\" becomes \"{1}\"")
        @CsvSource(delimiter = '|', value = {
            "BATGIRL VOL. 2: TO THE DEATH | Batgirl Vol. 2: To the Death",
            "WAIT... WHAT | Wait... What"
        })
        void shouldLowerWordWithPeriodBeforeNoLetter(final String title, final String expected) {
            assertThat(TitleCasing.fixAllCaps(title, ENGLISH)).isEqualTo(expected);
        }
    }
}
