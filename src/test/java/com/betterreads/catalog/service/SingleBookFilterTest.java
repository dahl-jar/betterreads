package com.betterreads.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Unit tests for {@link SingleBookFilter}. A series search returns single novels alongside boxed
 * sets, omnibus volumes, and split-part editions. Only single books should reach the catalog, so
 * the collections and part-splits are filtered out before staging.
 */
class SingleBookFilterTest {

    @ParameterizedTest(name = "\"{0}\" is not a single book")
    @CsvSource({
        "'The Wheel of Time (Boxed Set #1)'",
        "'The Wheel of Time, Books 1-4'",
        "'Wheel of Time, Books 5-9'",
        "'The Complete Wheel of Time Series Set'",
        "'The Sandman Volumes 1-10 Box'",
        "'The Eye of the World (part 1/2)'",
        "'The Great Hunt (part 2/2)'",
        "'The Way of Kings, Part 1'",
        "'Rhythm of War Part One'",
        "'The Stand: Part Two'"
    })
    @DisplayName("a boxed set, omnibus, or split-part edition is rejected")
    void rejectsCollectionsAndSplits(final String title) {
        assertThat(SingleBookFilter.isSingleBook(title)).isFalse();
    }

    @ParameterizedTest(name = "\"{0}\" is a single book")
    @CsvSource({
        "'The Eye of the World'",
        "'A Crown of Swords (The Wheel of Time, Book 7)'",
        "'New Spring'",
        "'The Great Hunt'"
    })
    @DisplayName("a single novel, including a numbered series volume, is kept")
    void keepsSingleNovels(final String title) {
        assertThat(SingleBookFilter.isSingleBook(title)).isTrue();
    }
}
