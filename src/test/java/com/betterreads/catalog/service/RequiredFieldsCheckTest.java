package com.betterreads.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.betterreads.catalog.service.RequiredFieldsCheck.MissingFields;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RequiredFieldsCheck}. A book may show only when it carries a title, at least
 * one author, a cover, a description of real length, and a publication year. Rating is not required.
 */
class RequiredFieldsCheckTest {

    private static final String TITLE = "Dune";

    private static final String AUTHOR = "Frank Herbert";

    private static final String COVER = "https://covers.openlibrary.org/b/id/1-L.jpg";

    private static final String REAL_DESCRIPTION = "A desert planet holds the universe's only source of spice.";

    private static final int YEAR = 1965;

    private static final String MISSING_TITLE = "title";

    private static final String MISSING_AUTHOR = "author";

    private static final String MISSING_COVER = "cover";

    private static final String MISSING_DESCRIPTION = "description";

    private static final String MISSING_YEAR = "year";

    private final RequiredFieldsCheck requiredFields = new RequiredFieldsCheck();

    @Test
    @DisplayName("a book with title, author, cover, description, and year is missing nothing")
    void completeBookIsMissingNothing() {
        final SourceBook book = complete().build();

        final MissingFields result = requiredFields.check(book);

        assertThat(result.isReady()).isTrue();
    }

    @Test
    @DisplayName("a book with no title is reported missing the title")
    void missingTitleIsReported() {
        final SourceBook book = complete().title(null).build();

        final MissingFields result = requiredFields.check(book);

        assertThat(result.missing()).containsExactly(MISSING_TITLE);
    }

    @Test
    @DisplayName("a whitespace-only title is reported missing the title")
    void blankTitleIsReported() {
        final SourceBook book = complete().title("   ").build();

        final MissingFields result = requiredFields.check(book);

        assertThat(result.missing())
            .as("a title of only spaces is not a title; it counts as missing")
            .containsExactly(MISSING_TITLE);
    }

    @Test
    @DisplayName("a book with no authors is reported missing the author")
    void missingAuthorIsReported() {
        final SourceBook book = complete().authors(null).build();

        final MissingFields result = requiredFields.check(book);

        assertThat(result.missing()).containsExactly(MISSING_AUTHOR);
    }

    @Test
    @DisplayName("a book with an empty author list is reported missing the author")
    void emptyAuthorListIsReported() {
        final SourceBook book = complete().authors(List.of()).build();

        final MissingFields result = requiredFields.check(book);

        assertThat(result.missing())
            .as("an empty list is distinct from null; both leave the book unshowable")
            .containsExactly(MISSING_AUTHOR);
    }

    @Test
    @DisplayName("a book with no cover is reported missing the cover")
    void missingCoverIsReported() {
        final SourceBook book = complete().coverUrl(null).build();

        final MissingFields result = requiredFields.check(book);

        assertThat(result.missing()).containsExactly(MISSING_COVER);
    }

    @Test
    @DisplayName("a book with no description is reported missing the description")
    void missingDescriptionIsReported() {
        final SourceBook book = complete().description(null).build();

        final MissingFields result = requiredFields.check(book);

        assertThat(result.missing()).containsExactly(MISSING_DESCRIPTION);
    }

    @Test
    @DisplayName("a description under twenty characters counts as missing")
    void tooShortDescriptionIsReported() {
        final SourceBook book = complete().description("Too short.").build();

        final MissingFields result = requiredFields.check(book);

        assertThat(result.missing())
            .as("a one-line catalog blurb is not a real description; the 20-char floor filters it")
            .containsExactly(MISSING_DESCRIPTION);
    }

    @Test
    @DisplayName("a book with no publication year is reported missing the year")
    void missingYearIsReported() {
        final SourceBook book = complete().publicationYear(null).build();

        final MissingFields result = requiredFields.check(book);

        assertThat(result.missing()).containsExactly(MISSING_YEAR);
    }

    @Test
    @DisplayName("a book missing several fields names all of them")
    void multipleMissingFieldsAreAllReported() {
        final SourceBook sparse = SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
            .title(TITLE)
            .build();

        final MissingFields result = requiredFields.check(sparse);

        assertThat(result.missing())
            .containsExactlyInAnyOrder(MISSING_AUTHOR, MISSING_COVER, MISSING_DESCRIPTION, MISSING_YEAR);
    }

    @Test
    @DisplayName("a book with no rating is still ready to show")
    void ratingIsNotRequired() {
        final SourceBook book = complete().averageRating(null).ratingCount(null).build();

        final MissingFields result = requiredFields.check(book);

        assertThat(result.isReady())
            .as("rating is excluded from the show bar; a complete book with no rating still shows")
            .isTrue();
    }

    private static SourceBook.Builder complete() {
        return SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
            .title(TITLE)
            .authors(SourceAuthor.ofNames(List.of(AUTHOR)))
            .coverUrl(COVER)
            .description(REAL_DESCRIPTION)
            .publicationYear(YEAR);
    }
}
