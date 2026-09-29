package com.betterreads.clients.hardcoverbook;

import com.betterreads.booksource.SourceBook;
import com.betterreads.clients.hardcover.HardcoverBookNode;
import com.betterreads.clients.hardcover.HardcoverBookNodeMapper;
import static com.betterreads.clients.hardcover.BookByIdJson.bookById;
import static com.betterreads.clients.hardcover.BookSearchJson.bookSearch;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HardcoverMapperTest {

    private static final String AUTHOR_ROLE = "Author";

    private static final String SNYDER = "Scott Snyder";

    private static final String COMPANIONS = "The Dark Tower Companions";

    private static final double RATING = 4.31;

    private static final int RATING_COUNT = 6394;

    private static final int RELEASE_YEAR = 1937;

    private static final int PAGES = 310;

    private static final int BEGINNINGS_VOLUME = 3;

    private static final String LAST_KING = "The Last King of Osten Ard";

    private static final String LETZTE_KOENIG = "Der letzte König von Osten Ard";

    private static final int LAST_KING_BOOKS = 4;

    private static final int SAGA_VOLUME = 4;

    private static final int SAGA_BOOKS = 7;

    private final HardcoverMapper mapper = new HardcoverMapper();

    private SourceBook map(final HardcoverDocument document) {
        return Objects.requireNonNull(mapper.toSourceBook(document));
    }

    private SourceBook map(final HardcoverBookNode node) {
        return HardcoverBookNodeMapper.toSourceBookWithSeries(node).orElseThrow();
    }

    @Nested
    @DisplayName("isbn13")
    class FirstIsbn13 {

        @Test
        void shouldPickTheFirstIsbn13OutOfAMixedList() {
            final String isbn13 = "9780792748663";
            final HardcoverDocument document = bookSearch().withIsbns("0792748662", isbn13, "8385432167").document();

            final SourceBook book = map(document);

            assertThat(book.isbn13()).isEqualTo(isbn13);
        }

        @Test
        void shouldReturnNullWhenNoIsbnsAreListed() {
            final HardcoverDocument document = bookSearch().withoutIsbns().document();

            final SourceBook book = map(document);

            assertThat(book.isbn13()).isNull();
        }
    }

    @Nested
    @DisplayName("series position")
    class SeriesPosition {

        @ParameterizedTest(name = "position {0} maps to volume {1}")
        @CsvSource({"2.0, 2", "0.5, ", "0.0, "})
        void shouldKeepOnlyWholePositionsFromOneUp(final double position, final Integer volume) {
            final HardcoverDocument document = bookSearch().withFeaturedSeries(COMPANIONS, position).document();

            final SourceBook book = map(document);

            assertThat(book.seriesPosition()).isEqualTo(volume);
        }
    }

    @Nested
    @DisplayName("toSourceBook from a search document")
    class FromSearchDocument {

        @Test
        void shouldCarryTheDocumentFieldsOntoTheBook() {
            final String series = "The Lord of the Rings";
            final HardcoverDocument document = bookSearch().withFeaturedSeries(series).document();

            final SourceBook book = map(document);

            assertThat(book)
                .extracting(SourceBook::hardcoverId, SourceBook::isbn13, SourceBook::publicationYear,
                    SourceBook::description, SourceBook::pageCount, SourceBook::averageRating,
                    SourceBook::ratingCount, SourceBook::coverUrl)
                .containsExactly("9999", "9788578276300", RELEASE_YEAR,
                    "Bilbo Baggins is swept into a quest for a dragon's hoard.", PAGES, RATING,
                    RATING_COUNT, "https://covers.example.test/hobbit.jpg");
            assertThat(book.rawSubjects()).contains("fantasy", "fiction");
            assertThat(book.seriesName()).isEqualTo(series);
            assertThat(book.seriesPosition()).isEqualTo(1);
        }

        @Test
        void shouldLeaveTheSeriesUnsetWhenTheFeaturedPositionIsMissing() {
            final HardcoverDocument document =
                bookSearch().withFeaturedSeries("The World of The Sun Eater", null).document();

            final SourceBook book = map(document);

            assertThat(book.seriesName()).isNull();
        }

        @Test
        void shouldLeaveTheSeriesNameUnsetWhenTheFeaturedSeriesIsUnnamed() {
            final HardcoverDocument document = bookSearch().withUnnamedFeaturedSeries().document();

            final SourceBook book = map(document);

            assertThat(book.seriesName()).isNull();
        }

        @Test
        void shouldReturnNullWhenTheDocumentHasNoTitle() {
            final HardcoverDocument document = bookSearch().withoutTitle().document();

            final SourceBook book = mapper.toSourceBook(document);

            assertThat(book).isNull();
        }

        @Test
        void shouldLeaveSubjectsUnsetWhenTheDocumentHasNoGenres() {
            final HardcoverDocument document = bookSearch().withoutGenres().document();

            final SourceBook book = map(document);

            assertThat(book.rawSubjects()).isNull();
        }
    }

    @Nested
    @DisplayName("authors from a search document")
    class AuthorsFromSearchDocument {

        @Test
        void shouldIgnoreEditorsWhenAnAuthorIsCredited() {
            final HardcoverDocument document = bookSearch().withoutCredits()
                .withCredit(AUTHOR_ROLE, SNYDER).withCredit("Editor", "Darrow").document();

            final SourceBook book = map(document);

            assertThat(book.authorNames()).containsExactly(SNYDER);
        }

        @Test
        void shouldFallBackToEditorsWhenNoAuthorIsCredited() {
            final String gorman = "Ed Gorman";
            final HardcoverDocument document = bookSearch().withoutCredits()
                .withCredit("Editor / Contributor", gorman).withCredit("Contributor", "David Morrell").document();

            final SourceBook book = map(document);

            assertThat(book.authorNames()).containsExactly(gorman);
        }

        @Test
        void shouldLeaveAuthorsUnsetWhenOnlyNonWritersAreCredited() {
            final HardcoverDocument document =
                bookSearch().withoutCredits().withCredit("Illustrator", "Greg Capullo").document();

            final SourceBook book = map(document);

            assertThat(book.authors()).isNull();
        }

        @Test
        void shouldUseTheAuthorNamesWhenNoCreditsAreListed() {
            final HardcoverDocument document = bookSearch().withoutCredits().document();

            final SourceBook book = map(document);

            assertThat(book.authorNames()).containsExactly("J.R.R. Tolkien", "Alan Lee");
        }
    }

    @Nested
    @DisplayName("toSourceBook from a book node")
    class FromBookNode {

        @Test
        void shouldTreatAnUnlabelledCreditAsAnAuthor() {
            final String uncredited = "Uncredited";
            final HardcoverBookNode node = bookById().withCredit(null, uncredited).node();

            final SourceBook book = map(node);

            assertThat(book.authorNames()).containsExactly(SNYDER, uncredited);
        }

        @Test
        void shouldSkipAFeaturedSeriesNamedAfterTheBook() {
            final String beginnings = "Stephen King's The Dark Tower: Beginnings";
            final HardcoverBookNode node = bookById().withTitle("Treachery").withoutSeries()
                .withSeries("The Dark Tower: Treachery", 1, true)
                .withSeries(COMPANIONS, null, false)
                .withSeries(beginnings, BEGINNINGS_VOLUME, false)
                .node();

            final SourceBook book = map(node);

            assertThat(book.seriesName()).isEqualTo(beginnings);
            assertThat(book.seriesPosition()).isEqualTo(BEGINNINGS_VOLUME);
        }

        @Test
        void shouldSkipOneBookSeries() {
            final HardcoverBookNode node = bookById().withoutSeries()
                .withSeries(LAST_KING, 1, false, LAST_KING_BOOKS)
                .withSeries("Osten Ard Saga", SAGA_VOLUME, false, SAGA_BOOKS)
                .withSeries(LETZTE_KOENIG, 1, true, 1)
                .node();

            final SourceBook book = map(node);

            assertThat(book.seriesName()).isEqualTo(LAST_KING);
            assertThat(book.seriesPosition()).isEqualTo(1);
        }

        @Test
        void shouldKeepLoneOneBookSeries() {
            final HardcoverBookNode node = bookById().withoutSeries()
                .withSeries(LETZTE_KOENIG, 1, true, 1)
                .node();

            final SourceBook book = map(node);

            assertThat(book.seriesName()).isEqualTo(LETZTE_KOENIG);
        }

        @Test
        void shouldKeepOneBookSeriesBesideUnnumbered() {
            final HardcoverBookNode node = bookById().withoutSeries()
                .withSeries(COMPANIONS, null, false, LAST_KING_BOOKS)
                .withSeries(LETZTE_KOENIG, 1, true, 1)
                .node();

            final SourceBook book = map(node);

            assertThat(book.seriesName()).isEqualTo(LETZTE_KOENIG);
        }

        @Test
        void shouldTrustUnknownBookCount() {
            final HardcoverBookNode node = bookById().withoutSeries()
                .withSeries(LAST_KING, 1, false, LAST_KING_BOOKS)
                .withSeries(LETZTE_KOENIG, 1, true)
                .node();

            final SourceBook book = map(node);

            assertThat(book.seriesName()).isEqualTo(LETZTE_KOENIG);
        }

        @Test
        void shouldLeaveTheSeriesUnsetWhenTheFeaturedMembershipIsUnnumbered() {
            final HardcoverBookNode node = bookById().withoutSeries().withSeries(COMPANIONS, null, true).node();

            final SourceBook book = map(node);

            assertThat(book.seriesName()).isNull();
        }
    }
}
