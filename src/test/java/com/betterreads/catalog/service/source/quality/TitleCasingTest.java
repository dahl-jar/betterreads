package com.betterreads.catalog.service.source.quality;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
        final String title = TitleCasing.capitalize(NORTHWEST_ANGLE, List.of("NORTHWEST ANGLE"), FRENCH);

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
        "The night  is watching | The Night  Is Watching"
    })
    void shouldTitleCaseLowercaseEnglishTitle(final String lowercase, final String expected) {
        assertThat(TitleCasing.capitalize(lowercase, List.of(), ENGLISH)).isEqualTo(expected);
    }

    @Test
    void shouldKeepDeliberateCasing() {
        final String title = "iPhone for seniors";

        assertThat(TitleCasing.capitalize(title, List.of(), ENGLISH)).isEqualTo(title);
    }

    @Test
    void shouldKeepNonEnglishTitle() {
        final String title = "La nuit des hiboux";

        assertThat(TitleCasing.capitalize(title, List.of(), FRENCH)).isEqualTo(title);
    }
}
