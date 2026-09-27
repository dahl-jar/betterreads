package com.betterreads.features.bookstaging;

import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.SourceBook;
import com.betterreads.booksource.SourceBooks;
import com.betterreads.features.bookstaging.RequiredFieldsCheck.MissingFields;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RequiredFieldsCheckTest {

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
            .as("a title of only spaces counts as missing")
            .containsExactly(MISSING_TITLE);
    }

    @Test
    @DisplayName("a book with an empty author list is reported missing the author")
    void emptyAuthorListIsReported() {
        final SourceBook book = complete().authors(List.of()).build();

        final MissingFields result = requiredFields.check(book);

        assertThat(result.missing())
            .as("an empty author list leaves the book unshowable, like a null one")
            .containsExactly(MISSING_AUTHOR);
    }

    static Stream<Arguments> descriptionsAroundTheFloor() {
        return Stream.of(
            arguments("Nineteen characters", true),
            arguments("Twenty characters ok", false),
            arguments("   Nineteen characters   ", true));
    }

    @ParameterizedTest(name = "\"{0}\" missing: {1}")
    @MethodSource("descriptionsAroundTheFloor")
    @DisplayName("should report a description under 20 characters after trimming as missing")
    void shouldReportDescriptionUnderTwentyCharactersAsMissing(final String description, final boolean missing) {
        final SourceBook book = complete().description(description).build();

        final MissingFields result = requiredFields.check(book);

        assertThat(result.missing().contains(MISSING_DESCRIPTION)).isEqualTo(missing);
    }

    @Test
    @DisplayName("a book missing several fields names all of them")
    void multipleMissingFieldsAreAllReported() {
        final SourceBook sparse = SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
            .title(DuneBooks.TITLE)
            .build();

        final MissingFields result = requiredFields.check(sparse);

        assertThat(result.missing())
            .containsExactlyInAnyOrder(
                MISSING_AUTHOR, MISSING_COVER, MISSING_DESCRIPTION, MISSING_YEAR, MISSING_ISBN);
    }

    private static SourceBook.Builder complete() {
        return SourceBooks.dune().toBuilder();
    }
}
