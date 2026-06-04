package com.betterreads.search.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.betterreads.catalog.entity.Author;
import com.betterreads.catalog.entity.Book;
import com.betterreads.catalog.service.BookFieldSource;
import com.betterreads.catalog.service.SourceBook;
import com.betterreads.search.dto.BookSearchDocument;
import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Maps a catalog {@link Book} to the search document, including the popularity score derived from
 * the rating volume and average.
 */
class BookSearchDocumentMapperTest {

    private static final String AUTHOR = "Brandon Sanderson";

    private static final String SUBJECT = "Fantasy";

    private static final String MISTBORN_KEY = "hc-1";

    private static final String MISTBORN_TITLE = "Mistborn";

    private static final String LANGUAGE = "en";

    private static final int YEAR = 2006;

    private static final int THOUSAND_RATINGS = 999;

    private static final double ROUND_AVERAGE = 4.0;

    private static final double EXPECTED_SCORE = 12.0;

    private static final double TOLERANCE = 1e-9;

    private final BookSearchDocumentMapper mapper = new BookSearchDocumentMapper();

    @Test
    @DisplayName("maps identity, display, and facet fields onto the document")
    void mapsFields() {
        final Book book = book(MISTBORN_KEY, builder -> builder
            .title(MISTBORN_TITLE)
            .language(LANGUAGE)
            .publicationYear(YEAR)
            .rawSubjects(List.of(SUBJECT)));

        final BookSearchDocument document = mapper.toDocument(book);

        assertThat(document)
            .extracting(
                BookSearchDocument::bookId,
                BookSearchDocument::title,
                BookSearchDocument::language,
                BookSearchDocument::publicationYear)
            .containsExactly(MISTBORN_KEY, MISTBORN_TITLE, LANGUAGE, YEAR);
        assertThat(document.authors()).containsExactly(AUTHOR);
        assertThat(document.subjects()).containsExactly(SUBJECT);
    }

    @Test
    @DisplayName("scores popularity as log10(1 + ratingCount) times the average rating")
    void scoresPopularity() {
        final Book book = book("hc-2", builder -> builder
            .title("Elantris")
            .ratingCount(THOUSAND_RATINGS)
            .averageRating(ROUND_AVERAGE));

        final BookSearchDocument document = mapper.toDocument(book);

        assertThat(document.popularityScore()).isEqualTo(EXPECTED_SCORE, within(TOLERANCE));
    }

    @Test
    @DisplayName("scores zero popularity when the book has no ratings")
    void scoresZeroWithoutRatings() {
        final Book book = book("hc-3", builder -> builder.title("Warbreaker"));

        final BookSearchDocument document = mapper.toDocument(book);

        assertThat(document.popularityScore()).isZero();
    }

    private static Book book(
        final String hardcoverId, final UnaryOperator<SourceBook.Builder> customize) {
        final SourceBook source = customize
            .apply(SourceBook.builder(BookFieldSource.HARDCOVER).hardcoverId(hardcoverId))
            .build();
        final Book book = new Book();
        book.applyFrom(source);
        final Author author = new Author();
        author.setName(AUTHOR);
        book.getAuthors().add(author);
        return book;
    }
}
