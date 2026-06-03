package com.betterreads.integration.googlebooks.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

import com.betterreads.catalog.service.SourceBook;
import com.betterreads.integration.googlebooks.dto.IndustryIdentifier;
import com.betterreads.integration.googlebooks.dto.Volume;
import com.betterreads.integration.googlebooks.dto.VolumeInfo;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Unit tests for the pure helpers inside {@link GoogleBooksMapper}. */
class GoogleBooksMapperTest {

    private final GoogleBooksMapper mapper = new GoogleBooksMapper();

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

    @Nested
    @DisplayName("subjects from categories")
    class Subjects {

        @Test
        @DisplayName("Google categories reduce into subjects so the merger can union them")
        void categoriesBecomeSubjects() {
            final SourceBook book = mapWith(
                info -> withCategories(info, List.of("Comics & Graphic Novels", "Fiction")));

            assertThat(book.rawSubjects())
                .as("Google's coarse shelf must reach subjects, not sit in an unread field")
                .contains("comics", "fiction");
        }

        @Test
        @DisplayName("no categories leaves subjects null so a refresh does not wipe another source's genres")
        void noCategoriesLeavesSubjectsNull() {
            final SourceBook book = mapWith(info -> withCategories(info, null));

            assertThat(book.rawSubjects())
                .as("null subjects mean 'field absent', distinct from an empty 'no genres'")
                .isNull();
        }
    }

    @Test
    @DisplayName("Google rating is not mapped; rating is Hardcover-only")
    void googleRatingIsNotMapped() {
        final SourceBook book = mapWith(info -> info);

        assertThat(book.averageRating())
            .as("Google's rating is not trusted; only Hardcover supplies a rating")
            .isNull();
        assertThat(book.ratingCount()).isNull();
    }

    private SourceBook mapWith(final UnaryOperator<VolumeInfo> customize) {
        final VolumeInfo base = new VolumeInfo(
            "Dune", null, List.of("Frank Herbert"), "1965", "Ace", 412, "en",
            null, null, 4.5, 9000, "A desert planet.");
        return Objects.requireNonNull(mapper.toSourceBook(new Volume("gb-1", customize.apply(base))));
    }

    private static VolumeInfo withCategories(final VolumeInfo info, final @Nullable List<String> categories) {
        return new VolumeInfo(
            info.title(), info.subtitle(), info.authors(), info.publishedDate(), info.publisher(),
            info.pageCount(), info.language(), info.industryIdentifiers(), categories,
            info.averageRating(), info.ratingsCount(), info.description());
    }
}
