package com.betterreads.integration.loc.mapper;

import static com.betterreads.integration.loc.LocRecords.sruResponse;
import static org.assertj.core.api.Assertions.assertThat;

import com.betterreads.catalog.service.source.model.BookFieldSource;
import com.betterreads.catalog.service.source.model.SourceBook;
import com.betterreads.integration.loc.LocRecords;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@SuppressWarnings("PMD.TooManyMethods")
class LocMapperTest {

    private static final int MARC_YEAR = 2019;

    private final LocMapper mapper = new LocMapper();

    private SourceBook map(final LocRecords record) {
        return mapper.toSourceBook(record.xml()).orElseThrow();
    }

    @Nested
    @DisplayName("toSourceBook")
    class ToSourceBook {

        @Test
        void shouldReadTheLccn() {
            final SourceBook book = map(sruResponse());

            assertThat(book.source()).isEqualTo(BookFieldSource.LOC);
            assertThat(book.locLccn()).isEqualTo("2019287107");
        }

        @ParameterizedTest(name = "{1}")
        @CsvSource(delimiter = '|', value = {
            "059309932X   | 9780593099322         | 9780593099322",
            "0312850093 : | 9780312850098 (v. 1)  | 9780312850098"
        })
        void shouldPickTheIsbn13AmongTheIsbns(final String isbn10, final String isbn13, final String expected) {
            final SourceBook book = map(sruResponse().withIsbns(isbn10, isbn13));

            assertThat(book.isbn13()).isEqualTo(expected);
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', value = {
            "xxxix, 272 pages, 36 pages of plates : illustrations ; 23 cm. | 272",
            "xiv, 670 p., [3] p. of plates : maps ; 24 cm.                 | 670",
            "1 v. (unpaged) : col. ill. ; 26 cm.                           |",
            "v. 1-3 : 658 pages                                            |"
        })
        void shouldReadTheLeadingPageCount(final String extent, final Integer pageCount) {
            final SourceBook book = map(sruResponse().withExtent(extent));

            assertThat(book.pageCount()).isEqualTo(pageCount);
        }

        @Test
        void shouldReadTheMarcYear() {
            final SourceBook book = map(sruResponse());

            assertThat(book.publicationYear()).isEqualTo(MARC_YEAR);
        }

        @ParameterizedTest(name = "encoding={0} point={1}")
        @CsvSource({", ", "marc, end"})
        void shouldIgnoreAnIssuedDateThatIsNotTheMarcStartYear(final String encoding, final String point) {
            final SourceBook book = map(sruResponse().withDateIssued("1965", encoding, point));

            assertThat(book.publicationYear()).isNull();
        }

        @Test
        void shouldReadTheLanguageCode() {
            final SourceBook book = map(sruResponse());

            assertThat(book.language()).isEqualTo("eng");
        }

        @Test
        void shouldReadTheNumberedSeries() {
            final SourceBook book = map(sruResponse());

            assertThat(book.seriesName()).isEqualTo("Dune chronicles");
            assertThat(book.seriesPosition()).isEqualTo(1);
        }

        @Test
        void shouldPreferTheNumberedSeriesOverAnImprint() {
            final String series = "Song of ice and fire";

            final SourceBook book = map(sruResponse().withoutSeries()
                .withSeries("Bantam spectra", null).withSeries(series, "bk. 2"));

            assertThat(book.seriesName()).isEqualTo(series);
            assertThat(book.seriesPosition()).isEqualTo(2);
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', value = {
            "Herbert, Frank,                        | Frank Herbert",
            "Tolkien, J. R. R. (John Ronald Reuel), | J. R. R. Tolkien",
            "Jordan, Robert.                        | Robert Jordan",
            "Martin, George R. R.                   | George R. R. Martin",
            "Homer                                  | Homer"
        })
        void shouldShowThePrimaryAuthorWithTheGivenNameFirst(final String namePart, final String author) {
            final SourceBook book = map(sruResponse().withPrimaryNamePart(namePart));

            assertThat(book.authorNames()).containsExactly(author);
        }

        @Test
        void shouldListCoAuthorsAfterThePrimaryAuthor() {
            final String author = "author";

            final SourceBook book = map(sruResponse().withoutNames()
                .withContributor("Jenkins, Christine,", author)
                .withPrimaryContributor("Cart, Michael,", author)
                .withContributor("Valka, Onderra,", "editor"));

            assertThat(book.authorNames()).containsExactly("Michael Cart", "Christine Jenkins", "Onderra Valka");
        }

        @Test
        void shouldDropNonWriterNames() {
            final SourceBook book = map(sruResponse().withoutNames()
                .withPrimaryContributor("David, Peter (Peter Allen),", "screenwriter")
                .withContributor("Lee, Jae,", "illustrator")
                .withContributor("Eliopoulos, Chris,", "letterer")
                .withContributor("King, Stephen,", null));

            assertThat(book.authorNames()).containsExactly("Peter David");
        }

        @ParameterizedTest(name = "{0}{1}")
        @CsvSource(delimiter = '|', value = {
            "'The ' | eye of the world | The eye of the world",
            "L'     | étranger         | L'étranger"
        })
        void shouldJoinTheLeadingArticleOntoTheTitle(final String article, final String title, final String expected) {
            final SourceBook book = map(sruResponse().withNonSort(article).withTitle(title));

            assertThat(book.title()).isEqualTo(expected);
        }

        @Test
        void shouldCarryTheSummaryAsTheDescription() {
            final SourceBook book = map(sruResponse());

            assertThat(book.description()).startsWith("Follows the adventures of Paul Atreides");
        }

        @Test
        void shouldReduceTheGenresToCanonicalTerms() {
            final SourceBook book = map(sruResponse());

            assertThat(book.rawSubjects()).containsExactlyInAnyOrder("science fiction", "fiction");
        }

        @Test
        void shouldReturnEmptyWhenTheResponseHasNoRecords() {
            assertThat(mapper.toSourceBook(sruResponse().withoutRecords().xml())).isEmpty();
        }

        @Test
        void shouldReturnEmptyWhenTheResponseIsNotXml() {
            assertThat(mapper.toSourceBook("Service Unavailable")).isEmpty();
        }
    }
}
