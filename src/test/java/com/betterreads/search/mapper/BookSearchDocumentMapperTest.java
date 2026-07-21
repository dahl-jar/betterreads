package com.betterreads.search.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.betterreads.catalog.dto.BookIndexView;
import com.betterreads.search.dto.BookSearchDocument;
import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Maps a catalog {@link BookIndexView} to the search document, including the popularity score
 * derived from the rating volume and average.
 */
class BookSearchDocumentMapperTest {

    private static final String AUTHOR = "Brandon Sanderson";

    private static final String SUBJECT = "Fantasy";

    private static final String MISTBORN_KEY = "hc-1";

    private static final String MISTBORN_TITLE = "Mistborn";

    private static final String SERIES = "Mistborn Era One";

    private static final int SERIES_POSITION = 1;

    private static final String LANGUAGE = "en";

    private static final String SERVED_COVER_URL =
        "https://api.example.com/api/v1/images/covers/hc-1?v=abc123";

    private static final int YEAR = 2006;

    private static final int RATING_COUNT = 999;

    private static final BigDecimal ROUND_AVERAGE = BigDecimal.valueOf(4.0);

    private static final double EXPECTED_SCORE = 12.0;

    private static final double TOLERANCE = 1e-9;

    private final BookSearchDocumentMapper mapper = new BookSearchDocumentMapper();

    @Test
    @DisplayName("maps identity, display, and facet fields onto the document")
    void mapsFields() {
        final BookIndexView book = new BookIndexView(
            MISTBORN_KEY, MISTBORN_TITLE, null, SERIES, SERIES_POSITION,
            List.of(AUTHOR), List.of(SUBJECT), LANGUAGE, SERVED_COVER_URL, YEAR, null, null);

        final BookSearchDocument document = mapper.toDocument(book);

        assertThat(document)
            .extracting(
                BookSearchDocument::bookId,
                BookSearchDocument::title,
                BookSearchDocument::seriesName,
                BookSearchDocument::seriesPosition,
                BookSearchDocument::language,
                BookSearchDocument::coverUrl,
                BookSearchDocument::publicationYear)
            .containsExactly(
                MISTBORN_KEY, MISTBORN_TITLE, SERIES, SERIES_POSITION, LANGUAGE, SERVED_COVER_URL, YEAR);
        assertThat(document.authors()).containsExactly(AUTHOR);
        assertThat(document.subjects()).containsExactly(SUBJECT);
    }

    @Test
    @DisplayName("scores popularity as log10(1 + ratingCount) times the average rating")
    void scoresPopularity() {
        final BookIndexView book = view("hc-2", "Elantris", RATING_COUNT, ROUND_AVERAGE);

        final BookSearchDocument document = mapper.toDocument(book);

        assertThat(document.popularityScore()).isEqualTo(EXPECTED_SCORE, within(TOLERANCE));
    }

    @Test
    @DisplayName("scores zero popularity when the book has no ratings")
    void scoresZeroWithoutRatings() {
        final BookIndexView book = view("hc-3", "Warbreaker", null, null);

        final BookSearchDocument document = mapper.toDocument(book);

        assertThat(document.popularityScore()).isZero();
    }

    private static BookIndexView view(
        final String dedupKey, final String title,
        final @Nullable Integer ratingCount, final @Nullable BigDecimal averageRating) {
        return new BookIndexView(
            dedupKey, title, null, null, null, List.of(), List.of(), null, null, null,
            averageRating, ratingCount);
    }
}
