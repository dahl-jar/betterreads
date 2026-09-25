package com.betterreads.integration.hardcover.mapper;

import static com.betterreads.integration.hardcover.BookByIdJson.bookById;
import static com.betterreads.integration.hardcover.BookSearchJson.bookSearch;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Objects;

import com.betterreads.catalog.service.source.model.SourceBook;
import com.betterreads.integration.hardcover.dto.HardcoverBookNode;
import com.betterreads.integration.hardcover.dto.HardcoverDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HardcoverMapperTest {

    private static final String AUTHOR = "Author";

    private static final String ISBN_10 = "0792748662";

    private static final String SNYDER = "Scott Snyder";

    private static final String COMPANIONS = "The Dark Tower Companions";

    private static final double RATING = 4.31;

    private static final int RATING_COUNT = 6394;

    private static final int BEGINNINGS_VOLUME = 3;

    private final HardcoverMapper mapper = new HardcoverMapper();

    private SourceBook map(final HardcoverDocument document) {
        return Objects.requireNonNull(mapper.toSourceBook(document));
    }

    private SourceBook map(final HardcoverBookNode node) {
        return Objects.requireNonNull(mapper.toSourceBook(node));
    }

    @Nested
    @DisplayName("firstIsbn13")
    class FirstIsbn13 {

        @Test
        void shouldPickTheFirstIsbn13OutOfAMixedList() {
            final String isbn13 = "9780792748663";

            final String picked = HardcoverMapper.firstIsbn13(List.of(ISBN_10, isbn13, "8385432167"));

            assertThat(picked).isEqualTo(isbn13);
        }

        @Test
        void shouldReturnNullWhenNoIsbn13IsListed() {
            assertThat(HardcoverMapper.firstIsbn13(List.of(ISBN_10))).isNull();
        }
    }

    @Nested
    @DisplayName("seriesPosition")
    class SeriesPosition {

        @ParameterizedTest(name = "position {0} maps to volume {1}")
        @CsvSource({"2.0, 2", "0.5, ", "0.0, "})
        void shouldKeepOnlyWholePositionsFromOneUp(final double position, final Integer volume) {
            assertThat(HardcoverMapper.seriesPosition(position)).isEqualTo(volume);
        }
    }

    @Nested
    @DisplayName("toSourceBook from a search document")
    class FromSearchDocument {

        @Test
        void shouldCarryTheDocumentFieldsOntoTheBook() {
            final String series = "The Lord of the Rings";

            final SourceBook book = map(bookSearch().withFeaturedSeries(series).document());

            assertThat(book.hardcoverId()).isEqualTo("9999");
            assertThat(book.isbn13()).isEqualTo("9788578276300");
            assertThat(book.averageRating()).isEqualTo(RATING);
            assertThat(book.ratingCount()).isEqualTo(RATING_COUNT);
            assertThat(book.coverUrl()).isEqualTo("https://assets.hardcover.app/hobbit.jpg");
            assertThat(book.rawSubjects()).contains("fantasy", "fiction");
            assertThat(book.seriesName()).isEqualTo(series);
            assertThat(book.seriesPosition()).isEqualTo(1);
        }

        @Test
        void shouldLeaveTheSeriesUnsetWhenTheFeaturedPositionIsMissing() {
            final SourceBook book = map(
                bookSearch().withFeaturedSeries("The World of The Sun Eater", null).document());

            assertThat(book.seriesName()).isNull();
        }

        @Test
        void shouldReturnNullWhenTheDocumentHasNoTitle() {
            assertThat(mapper.toSourceBook(bookSearch().withoutTitle().document())).isNull();
        }

        @Test
        void shouldLeaveSubjectsUnsetWhenTheDocumentHasNoGenres() {
            final SourceBook book = map(bookSearch().withoutGenres().document());

            assertThat(book.rawSubjects()).isNull();
        }
    }

    @Nested
    @DisplayName("authors from a search document")
    class AuthorsFromSearchDocument {

        @Test
        void shouldIgnoreEditorsWhenAnAuthorIsCredited() {
            final SourceBook book = map(bookSearch().withoutCredits()
                .withCredit(AUTHOR, SNYDER).withCredit("Editor", "Darrow").document());

            assertThat(book.authorNames()).containsExactly(SNYDER);
        }

        @Test
        void shouldFallBackToEditorsWhenNoAuthorIsCredited() {
            final String gorman = "Ed Gorman";

            final SourceBook book = map(bookSearch().withoutCredits()
                .withCredit("Editor / Contributor", gorman).withCredit("Contributor", "David Morrell").document());

            assertThat(book.authorNames()).containsExactly(gorman);
        }

        @Test
        void shouldLeaveAuthorsUnsetWhenOnlyNonWritersAreCredited() {
            final SourceBook book = map(
                bookSearch().withoutCredits().withCredit("Illustrator", "Greg Capullo").document());

            assertThat(book.authors()).isNull();
        }

        @Test
        void shouldUseTheAuthorNamesWhenNoCreditsAreListed() {
            final SourceBook book = map(bookSearch().withoutCredits().document());

            assertThat(book.authorNames()).containsExactly("J.R.R. Tolkien", "Alan Lee");
        }
    }

    @Nested
    @DisplayName("toSourceBook from a book node")
    class FromBookNode {

        @Test
        void shouldTreatAnUnlabelledCreditAsAnAuthor() {
            final String uncredited = "Uncredited";

            final SourceBook book = map(bookById().withCredit(null, uncredited).node());

            assertThat(book.authorNames()).containsExactly(SNYDER, uncredited);
        }

        @Test
        void shouldSkipAFeaturedSeriesNamedAfterTheBook() {
            final String beginnings = "Stephen King's The Dark Tower: Beginnings";

            final SourceBook book = map(bookById().withTitle("Treachery").withoutSeries()
                .withSeries("The Dark Tower: Treachery", 1, true)
                .withSeries(COMPANIONS, null, false)
                .withSeries(beginnings, BEGINNINGS_VOLUME, false)
                .node());

            assertThat(book.seriesName()).isEqualTo(beginnings);
            assertThat(book.seriesPosition()).isEqualTo(BEGINNINGS_VOLUME);
        }

        @Test
        void shouldLeaveTheSeriesUnsetWhenTheFeaturedMembershipIsUnnumbered() {
            final SourceBook book = map(
                bookById().withoutSeries().withSeries(COMPANIONS, null, true).node());

            assertThat(book.seriesName()).isNull();
        }
    }
}
