package com.betterreads.integration.googlebooks.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.betterreads.integration.googlebooks.dto.IndustryIdentifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Unit tests for the pure helpers inside {@link GoogleBooksMapper}. */
class GoogleBooksMapperTest {

    @Nested
    @DisplayName("parseYear")
    class ParseYear {

        @ParameterizedTest(name = "publishedDate \"{0}\" parses to year {1}")
        @CsvSource({
            "'1990',       1990",
            "'2021-10-05', 2021"
        })
        void parsesVariablePrecisionDates(final String input, final int expected) {
            assertThat(GoogleBooksMapper.parseYear(input)).isEqualTo(expected);
        }

        @Test
        @DisplayName("input not starting with four digits is null, not Integer.valueOf(null) throwing")
        void garbageInputReturnsNull() {
            assertThat(GoogleBooksMapper.parseYear("circa 1990")).isNull();
        }
    }

    @Test
    @DisplayName("pageCount=0 (Google's reprint marker) maps to null so the catalog has no fake length")
    void nullIfZeroDropsTheReprintMarker() {
        assertThat(GoogleBooksMapper.nullIfZero(0)).isNull();
    }

    @Nested
    @DisplayName("findIsbn13")
    class FindIsbn13 {

        @Test
        @DisplayName("picks ISBN_13 when present alongside ISBN_10, never synthesizes from the 10")
        void picksIsbn13OverIsbn10() {
            final String isbn13 = "9781250832368";
            final List<IndustryIdentifier> identifiers = List.of(
                new IndustryIdentifier("ISBN_10", "1250832365"),
                new IndustryIdentifier("ISBN_13", isbn13)
            );
            assertThat(GoogleBooksMapper.findIsbn13(identifiers)).isEqualTo(isbn13);
        }

        @Test
        @DisplayName("returns null when only the OTHER microfilm identifier is present (Clash of Kings shape)")
        void otherOnlyReturnsNull() {
            final List<IndustryIdentifier> identifiers = List.of(
                new IndustryIdentifier("OTHER", "UOM:39015046463629")
            );
            assertThat(GoogleBooksMapper.findIsbn13(identifiers)).isNull();
        }
    }

    @Test
    @DisplayName("stripHtml drops the tag set Google ships and decodes entities the reader would see")
    void stripsRealGoogleDescriptionMarkup() {
        final String input = "<p><b><i>The Eye of the World</i></b><br>"
            + "Robert Jordan&#39;s &amp; the start of <i>The Wheel of Time</i></p>";
        assertThat(GoogleBooksMapper.stripHtml(input))
            .isEqualTo("The Eye of the WorldRobert Jordan's & the start of The Wheel of Time");
    }
}
