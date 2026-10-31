package com.betterreads.bookmerge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TitleCleanerTest {

    private static final List<String> AUTHOR_NAMES = List.of("Other Author", "J.R.R. Tolkien");

    @ParameterizedTest(name = "\"{0}\" cleans to \"{1}\"")
    @CsvSource({
        "'Watchmen (2019 Edition)',                         'Watchmen'",
        "'The Eye of the World (The Wheel of Time, Book 1)', 'The Eye of the World'",
        "'Dune (Book 1)',                                    'Dune'",
        "'The Great Hunt (The Wheel of Time Book 2)',        'The Great Hunt'",
        "'Sandman (Deluxe Edition)',                         'Sandman'",
        "'The Hobbit: Illustrated Edition',                  'The Hobbit'",
        "'The Sandman: The Deluxe Edition Book Three',       'The Sandman: Book Three'",
        "'The Sandman: The Deluxe Edition, Book One',        'The Sandman: Book One'",
        "'Rhythm of War Part One',                           'Rhythm of War'",
        "'The Way of Kings, Part 1',                         'The Way of Kings'"
    })
    void shouldStripEditionAndBookNumberTags(final String raw, final String expected) {
        assertThat(TitleCleaner.clean(raw)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "\"{0}\" cleans to \"{1}\"")
    @CsvSource({
        "'30 Days of Night Movie Novelization',                  '30 Days of Night'",
        "'30 Days of Night: Official Novelization of The Film',  '30 Days of Night'",
        "'30 days of night : a novelization',                    '30 days of night'",
        "'Alien Official Novelization',                          'Alien'"
    })
    void shouldStripANovelizationLabel(final String raw, final String expected) {
        assertThat(TitleCleaner.clean(raw)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "\"{0}\" is left unchanged")
    @CsvSource({
        "'The Wheel of Time Book 5'",
        "'The Lord of the Rings (The Fellowship of the Ring)'",
        "'Dune: Messiah'",
        "'The Art of Novelization'"
    })
    void shouldKeepTitleWithoutEditionMarker(final String title) {
        assertThat(TitleCleaner.clean(title)).isEqualTo(title);
    }

    @Test
    void shouldTrimAfterStripping() {
        assertThat(TitleCleaner.clean("  Watchmen (2019 Edition)")).isEqualTo("Watchmen");
    }

    @ParameterizedTest(name = "\"{0}\" becomes \"{1}\"")
    @CsvSource(delimiter = '|', value = {
        "Batman : Venom | Batman: Venom",
        "The Rose Tattoo ; Camino Real ; Orpheus Descending | The Rose Tattoo; Camino Real; Orpheus Descending"
    })
    void shouldDropTheSpaceBeforeColonOrSemicolon(final String raw, final String expected) {
        assertThat(TitleCleaner.closePunctuation(raw)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "\"{0}\" becomes \"{1}\"")
    @CsvSource(delimiter = '|', value = {
        "Fathomless riches ; or how I went from pop to pulpit | Fathomless riches: or how I went from pop to pulpit",
        "Moby Dick ; Or, the Whale | Moby Dick: Or, the Whale"
    })
    void shouldTurnSemicolonBeforeOrIntoColon(final String raw, final String expected) {
        assertThat(TitleCleaner.closePunctuation(raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "The Hobbit",
        "Murder by Agatha Christie",
        "J.R.R. Tolkien",
        "By J.R.R. Tolkien",
        "The Hobbit by J.R.R. Tolkien Again",
        "Hi"
    })
    void shouldTreatTitleAsClean(final String title) {
        assertThat(TitleCleaner.isClean(title, AUTHOR_NAMES)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"The Hobbit by J.R.R. Tolkien", "The Hobbit BY J.R.R. TOLKIEN", "RETURN OF THE KING"})
    void shouldNotTreatTitleAsClean(final String title) {
        assertThat(TitleCleaner.isClean(title, AUTHOR_NAMES)).isFalse();
    }

    @Test
    void shouldIgnoreSpaceAroundCreditName() {
        assertThat(TitleCleaner.isClean("The Hobbit by J.R.R. Tolkien", List.of(" J.R.R. Tolkien "))).isFalse();
    }
}
