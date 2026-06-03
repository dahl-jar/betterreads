package com.betterreads.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import com.betterreads.catalog.service.RequiredFieldsCheck.MissingFields;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Unit tests for {@link RequiredFieldsCheck}. A book may show only when it carries a title, at least
 * one author, a cover, a description of real length, a publication year, and an ISBN. Rating is not
 * required.
 */
class RequiredFieldsCheckTest {

    private static final String TITLE = "Dune";

    private static final String AUTHOR = "Frank Herbert";

    private static final String COVER = "https://covers.openlibrary.org/b/id/1-L.jpg";

    private static final String REAL_DESCRIPTION = "A desert planet holds the universe's only source of spice.";

    private static final int YEAR = 1965;

    private static final String ISBN = "9780441013593";

    private static final String MISSING_TITLE = "title";

    private static final String MISSING_AUTHOR = "author";

    private static final String MISSING_COVER = "cover";

    private static final String MISSING_DESCRIPTION = "description";

    private static final String MISSING_YEAR = "year";

    private static final String MISSING_ISBN = "isbn";

    private final RequiredFieldsCheck requiredFields = new RequiredFieldsCheck();

    @Test
    @DisplayName("a book with title, author, cover, description, year, and ISBN is missing nothing")
    void completeBookIsMissingNothing() {
        final SourceBook book = complete().build();

        final MissingFields result = requiredFields.check(book);

        assertThat(result.isReady()).isTrue();
    }

    @ParameterizedTest(name = "a book with no {0} is reported missing it")
    @MethodSource("eachRequiredFieldAbsent")
    @DisplayName("each required field, when absent, is the one field reported missing")
    void eachAbsentRequiredFieldIsReported(
        final String field, final UnaryOperator<SourceBook.Builder> absent) {
        final SourceBook book = absent.apply(complete()).build();

        final MissingFields result = requiredFields.check(book);

        assertThat(result.missing()).containsExactly(field);
    }

    static Stream<Arguments> eachRequiredFieldAbsent() {
        return Stream.of(
            arguments(MISSING_TITLE, (UnaryOperator<SourceBook.Builder>) b -> b.title(null)),
            arguments(MISSING_AUTHOR, (UnaryOperator<SourceBook.Builder>) b -> b.authors(null)),
            arguments(MISSING_COVER, (UnaryOperator<SourceBook.Builder>) b -> b.coverUrl(null)),
            arguments(MISSING_DESCRIPTION,
                (UnaryOperator<SourceBook.Builder>) b -> b.description(null)),
            arguments(MISSING_YEAR, (UnaryOperator<SourceBook.Builder>) b -> b.publicationYear(null)),
            arguments(MISSING_ISBN, (UnaryOperator<SourceBook.Builder>) b -> b.isbn13(null)));
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
    @DisplayName("a book with an empty author list is reported missing the author")
    void emptyAuthorListIsReported() {
        final SourceBook book = complete().authors(List.of()).build();

        final MissingFields result = requiredFields.check(book);

        assertThat(result.missing())
            .as("an empty list is distinct from null; both leave the book unshowable")
            .containsExactly(MISSING_AUTHOR);
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
    @DisplayName("a book missing several fields names all of them")
    void multipleMissingFieldsAreAllReported() {
        final SourceBook sparse = SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
            .title(TITLE)
            .build();

        final MissingFields result = requiredFields.check(sparse);

        assertThat(result.missing())
            .containsExactlyInAnyOrder(
                MISSING_AUTHOR, MISSING_COVER, MISSING_DESCRIPTION, MISSING_YEAR, MISSING_ISBN);
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
            .publicationYear(YEAR)
            .isbn13(ISBN);
    }
}
