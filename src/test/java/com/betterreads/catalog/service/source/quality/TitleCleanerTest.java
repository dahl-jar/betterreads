package com.betterreads.catalog.service.source.quality;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TitleCleanerTest {

    @ParameterizedTest(name = "\"{0}\" cleans to \"{1}\"")
    @CsvSource({
        "'Watchmen (2019 Edition)',                         'Watchmen'",
        "'The Eye of the World (The Wheel of Time, Book 1)', 'The Eye of the World'",
        "'Dune (Book 1)',                                    'Dune'",
        "'A Crown of Swords (The Wheel of Time, Book 7)',    'A Crown of Swords'",
        "'The Great Hunt (The Wheel of Time Book 2)',        'The Great Hunt'",
        "'Sandman (Deluxe Edition)',                         'Sandman'",
        "'The Hobbit: Illustrated Edition',                  'The Hobbit'",
        "'The Hobbit: 75th Anniversary Edition',            'The Hobbit'",
        "'The Sandman: The Deluxe Edition Book Three',       'The Sandman: Book Three'",
        "'The Sandman: The Deluxe Edition, Book One',        'The Sandman: Book One'",
        "'Rhythm of War Part One',                           'Rhythm of War'",
        "'The Way of Kings, Part 1',                         'The Way of Kings'"
    })
    @DisplayName("a trailing tag carrying an edition or book-number marker is stripped")
    void stripsEditionAndBookNumberTags(final String raw, final String expected) {
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
        "'Dune'",
        "'The Lord of the Rings (The Fellowship of the Ring)'",
        "'Dune: Messiah'",
        "'The Art of Novelization'"
    })
    @DisplayName("a title with no edition marker is returned unchanged")
    void leavesTitleWithoutEditionMarkerUnchanged(final String title) {
        assertThat(TitleCleaner.clean(title)).isEqualTo(title);
    }

    @Test
    @DisplayName("surrounding whitespace is trimmed after stripping")
    void trimsAfterStripping() {
        assertThat(TitleCleaner.clean("Watchmen  (2019 Edition)  ")).isEqualTo("Watchmen");
    }
}
