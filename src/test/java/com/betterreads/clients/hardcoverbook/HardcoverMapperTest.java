package com.betterreads.clients.hardcoverbook;

import com.betterreads.booksource.CreditRole;
import com.betterreads.booksource.SourceAuthor;
import com.betterreads.booksource.SourceBook;
import com.betterreads.clients.hardcover.BookSearchJson;
import com.betterreads.clients.hardcover.HardcoverBookNode;
import com.betterreads.clients.hardcover.HardcoverBookNodeMapper;
import com.betterreads.clients.hardcover.HardcoverCategories;
import com.betterreads.clients.hardcover.HardcoverCredits;
import com.betterreads.clients.hardcover.SearchHitFields;
import static com.betterreads.clients.hardcover.BookByIdJson.LAST_KING;
import static com.betterreads.clients.hardcover.BookByIdJson.LAST_KING_BOOKS;
import static com.betterreads.clients.hardcover.BookByIdJson.LETZTE_KOENIG;
import static com.betterreads.clients.hardcover.BookByIdJson.bookById;
import static com.betterreads.clients.hardcover.BookSearchJson.bookSearch;
import static com.betterreads.clients.hardcover.SearchHitFields.compilation;
import static com.betterreads.clients.hardcover.SearchHitFields.featuredSeries;
import static com.betterreads.clients.hardcover.SearchHitFields.noGenres;
import static com.betterreads.clients.hardcover.SearchHitFields.noIsbns;
import static com.betterreads.clients.hardcover.SearchHitFields.noTitle;
import static com.betterreads.clients.hardcover.SearchHitFields.unnamedFeaturedSeries;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class HardcoverMapperTest {

    private static final String SNYDER = "Scott Snyder";

    private static final String BOOK_EDITOR = "Book Editor";

    private static final String CAPULLO = "Greg Capullo";

    private static final String SIMONETTI = "Marc Simonetti";

    private static final String ROTHFUSS = "Patrick Rothfuss";

    private static final String COMPANIONS = "The Dark Tower Companions";

    private static final int BEGINNINGS_VOLUME = 3;

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
            final String badCheckDigit = "9780792748662";
            final HardcoverDocument document =
                bookSearch().withIsbns("0792748662", badCheckDigit, isbn13, "8385432167").document();

            final SourceBook book = map(document);

            assertThat(book.isbn13()).isEqualTo(isbn13);
        }

        @Test
        void shouldReturnNullWhenNoIsbnsAreListed() {
            final HardcoverDocument document = bookSearch().with(noIsbns()).document();

            final SourceBook book = map(document);

            assertThat(book.isbn13()).isNull();
        }
    }

    @Nested
    @DisplayName("series position")
    class SeriesPosition {

        @ParameterizedTest(name = "position {0} is kept as {1}")
        @CsvSource({
            "2.0, 2.0", "2.5, 2.5", "0.5, 0.5", "1.756, 1.76", "9999.99, 9999.99", "0.0, ", "0.004, ", "10000, "
        })
        void shouldKeepAPositionBetweenZeroAndTenThousand(final double position, final Double kept) {
            final HardcoverDocument document = bookSearch().with(featuredSeries(COMPANIONS, position)).document();

            final SourceBook book = map(document);

            assertThat(book.seriesPosition()).isEqualTo(kept);
        }
    }

    @Nested
    @DisplayName("book from a search document")
    class FromSearchDocument {

        @Test
        void shouldCarryTheDocumentFieldsOntoTheBook() {
            final HardcoverDocument document =
                bookSearch().with(featuredSeries(BookSearchJson.LORD_OF_THE_RINGS)).document();

            final SourceBook book = map(document);

            assertThat(book)
                .extracting(SourceBook::hardcoverId, SourceBook::isbn13, SourceBook::publicationYear,
                    SourceBook::description, SourceBook::pageCount, SourceBook::averageRating,
                    SourceBook::ratingCount, SourceBook::coverUrl)
                .containsExactly(BookSearchJson.HOBBIT_ID, BookSearchJson.HOBBIT_ISBN, BookSearchJson.HOBBIT_YEAR,
                    BookSearchJson.HOBBIT_DESCRIPTION, BookSearchJson.HOBBIT_PAGES, BookSearchJson.HOBBIT_RATING,
                    BookSearchJson.HOBBIT_RATING_COUNT, BookSearchJson.HOBBIT_COVER);
            assertThat(book.rawSubjects()).contains("fantasy", "fiction");
            assertThat(book.seriesName()).isEqualTo(BookSearchJson.LORD_OF_THE_RINGS);
            assertThat(book.seriesPosition()).isEqualTo(1);
        }

        @Test
        void shouldLeaveTheSeriesUnsetWhenTheFeaturedPositionIsMissing() {
            final HardcoverDocument document =
                bookSearch().with(featuredSeries("The World of The Sun Eater", null)).document();

            final SourceBook book = map(document);

            assertThat(book.seriesName()).isNull();
        }

        @Test
        void shouldLeaveTheSeriesNameUnsetWhenTheFeaturedSeriesIsUnnamed() {
            final HardcoverDocument document = bookSearch().with(unnamedFeaturedSeries()).document();

            final SourceBook book = map(document);

            assertThat(book.seriesName()).isNull();
        }

        @Test
        void shouldReturnNullWhenTheDocumentHasNoTitle() {
            final HardcoverDocument document = bookSearch().with(noTitle()).document();

            final SourceBook book = mapper.toSourceBook(document);

            assertThat(book).isNull();
        }

        @Test
        void shouldLeaveSubjectsUnsetWhenTheDocumentHasNoGenres() {
            final HardcoverDocument document = bookSearch().with(noGenres()).document();

            final SourceBook book = map(document);

            assertThat(book.rawSubjects()).isNull();
        }
    }

    @Nested
    @DisplayName("compilation from a search document")
    class CompilationFromSearchDocument {

        @ParameterizedTest(name = "category {0}")
        @NullSource
        @ValueSource(ints = HardcoverCategories.PROSE)
        void shouldDropTheSeriesOfAProseCompilation(final @Nullable Integer category) {
            final HardcoverDocument document = compiled(category);

            final SourceBook book = map(document);

            assertThat(book)
                .extracting(SourceBook::seriesName, SourceBook::seriesPosition, SourceBook::series)
                .containsExactly(null, null, List.of());
        }

        @Test
        void shouldKeepTheSeriesOfAGraphicNovelCompilation() {
            final HardcoverDocument document = compiled(HardcoverCategories.GRAPHIC_NOVEL);

            final SourceBook book = map(document);

            assertThat(book.seriesName()).isEqualTo(BookSearchJson.LORD_OF_THE_RINGS);
        }

        @Test
        void shouldKeepTheSeriesWhenTheCompilationFlagIsMissing() {
            final HardcoverDocument document = bookSearch().with(featuredSeries(BookSearchJson.LORD_OF_THE_RINGS))
                .with(SearchHitFields.noCompilationFlag()).document();

            final SourceBook book = map(document);

            assertThat(book.seriesName()).isEqualTo(BookSearchJson.LORD_OF_THE_RINGS);
        }

        @Test
        void shouldKeepTheOtherFieldsOfAProseCompilation() {
            final HardcoverDocument document = compiled(HardcoverCategories.PROSE);

            final SourceBook book = map(document);

            assertThat(book)
                .extracting(SourceBook::title, SourceBook::coverUrl, SourceBook::description)
                .containsExactly(BookSearchJson.HOBBIT_TITLE, BookSearchJson.HOBBIT_COVER,
                    BookSearchJson.HOBBIT_DESCRIPTION);
            assertThat(book.authors())
                .extracting(SourceAuthor::name)
                .containsExactly(BookSearchJson.TOLKIEN, BookSearchJson.ALAN_LEE);
        }

        @Test
        void shouldMarkAProseCompilation() {
            final HardcoverDocument document = compiled(HardcoverCategories.PROSE);

            final SourceBook book = map(document);

            assertThat(book.proseCompilation()).isTrue();
        }

        @Test
        void shouldLeaveAGraphicNovelCompilationUnmarked() {
            final HardcoverDocument document = compiled(HardcoverCategories.GRAPHIC_NOVEL);

            final SourceBook book = map(document);

            assertThat(book.proseCompilation()).isFalse();
        }

        private HardcoverDocument compiled(final @Nullable Integer category) {
            return bookSearch().with(featuredSeries(BookSearchJson.LORD_OF_THE_RINGS))
                .with(compilation(category)).document();
        }
    }

    @Nested
    @DisplayName("authors from a search document")
    class AuthorsFromSearchDocument {

        @Test
        void shouldKeepEditorAlongsideAuthorInOrder() {
            final HardcoverDocument document = bookSearch().withoutCredits()
                .withCredit("Editor", BOOK_EDITOR).withCredit(HardcoverCredits.AUTHOR, SNYDER).document();

            final SourceBook book = map(document);

            assertThat(book.authorNames()).containsExactly(BOOK_EDITOR, SNYDER);
        }

        @Test
        void shouldKeepIllustratorWithRole() {
            final HardcoverDocument document =
                bookSearch().withoutCredits().withCredit(HardcoverCredits.ILLUSTRATOR, CAPULLO).document();

            final SourceBook book = map(document);

            assertThat(book.authors()).containsExactly(SourceAuthor.withRole(CAPULLO, CreditRole.ILLUSTRATOR));
        }

        @Test
        void shouldKeepCreditOrder() {
            final HardcoverDocument document = bookSearch().withoutCredits()
                .withCredit("Cover Artist", SIMONETTI).withCredit(HardcoverCredits.AUTHOR, ROTHFUSS).document();

            final SourceBook book = map(document);

            assertThat(book.authors())
                .extracting(SourceAuthor::name)
                .containsExactly(SIMONETTI, ROTHFUSS);
        }

        @Test
        void shouldUseTheAuthorNamesWhenNoCreditsAreListed() {
            final HardcoverDocument document = bookSearch().withoutCredits().document();

            final SourceBook book = map(document);

            assertThat(book.authorNames()).containsExactly(BookSearchJson.TOLKIEN, BookSearchJson.ALAN_LEE);
        }
    }

    @Nested
    @DisplayName("book from a book node")
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
