package com.betterreads.clients.hardcoverbook;

import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.SeriesEntry;
import com.betterreads.booksource.SourceBook;
import com.betterreads.clients.hardcover.BookByIdJson;
import com.betterreads.clients.hardcover.BookSearchJson;
import com.betterreads.clients.hardcover.HardcoverProperties;
import com.betterreads.clients.hardcover.HardcoverWebClientConfig;
import com.betterreads.clients.hardcover.HardcoverWireMock;
import static com.betterreads.clients.hardcover.BookByIdJson.bookById;
import static com.betterreads.clients.hardcover.BookSearchJson.bookSearch;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@SpringBootTest(
    classes = {
        HardcoverWebClientConfig.class,
        HardcoverClientImpl.class,
        HardcoverMapper.class
    },
    properties = "spring.main.web-application-type=none"
)
@EnableConfigurationProperties(HardcoverProperties.class)
class HardcoverClientWireMockTest extends HardcoverWireMock {

    private static final String HOBBIT_TITLE = "The Hobbit";

    private static final String HOBBIT_AUTHOR = "J.R.R. Tolkien";

    private static final int HOBBIT_CANONICAL_RATING_COUNT = 6394;

    private static final String ABSOLUTE_BATMAN_ID = "2235304";

    private static final String ABSOLUTE_BATMAN_ISBN = "9781799507505";

    private static final String NON_NUMERIC_ID = "not-a-number";

    private static final String COLLECTED_SERIES = "Absolute Batman (2024)";

    private static final String SEARCH_OPERATION = "Search";

    private static final String BY_ID_OPERATION = "BookById";

    @Autowired
    private HardcoverClientImpl client;

    @Nested
    @DisplayName("hit selection")
    class HitSelection {

        @Test
        @DisplayName("picks the highest-read hit over the one-rating stub at rank 0")
        void picksHighestReadHitNotRankZero() {
            stubGraphQl(bookSearch());

            final Optional<SourceBook> result = client.fetchByTitleAuthor(HOBBIT_TITLE, HOBBIT_AUTHOR);

            assertThat(result)
                .get()
                .satisfies(book -> {
                    assertThat(book.source()).isEqualTo(BookFieldSource.HARDCOVER);
                    assertThat(book.ratingCount())
                        .as("should skip the one-rating stub at rank 0")
                        .isEqualTo(HOBBIT_CANONICAL_RATING_COUNT);
                });
        }

        @Test
        @DisplayName("a picked hit whose title does not match the query is rejected as drift")
        void titleDriftRejected() {
            stubGraphQl(bookSearch().withTitle("A Completely Different Book"));

            final Optional<SourceBook> book = client.fetchByTitleAuthor(HOBBIT_TITLE, HOBBIT_AUTHOR);

            assertThat(book)
                .as("should reject a hit whose title drifted from the query")
                .isEmpty();
        }

        @Test
        @DisplayName("a hit with the right title but a different author is rejected")
        void authorDriftRejected() {
            stubGraphQl(bookSearch().withAuthors("Some Other Author"));

            final Optional<SourceBook> book = client.fetchByTitleAuthor(HOBBIT_TITLE, HOBBIT_AUTHOR);

            assertThat(book)
                .as("should reject a shared title under another author")
                .isEmpty();
        }

        @Test
        void shouldRankHitsByReadsBeforeRatings() {
            stubGraphQl(bookSearch().withReadCount(0));

            final Optional<SourceBook> book = client.fetchByIsbn(ABSOLUTE_BATMAN_ISBN);

            assertThat(book).get().extracting(SourceBook::ratingCount).isEqualTo(1);
        }

        @Test
        void shouldRankByRatingsWhenReadsAreMissing() {
            stubGraphQl(bookSearch().withoutReadCount());

            final Optional<SourceBook> book = client.fetchByIsbn(ABSOLUTE_BATMAN_ISBN);

            assertThat(book).get().extracting(SourceBook::ratingCount).isEqualTo(HOBBIT_CANONICAL_RATING_COUNT);
        }

        @Test
        void shouldRejectAHitWithoutAuthorNames() {
            stubGraphQl(bookSearch().withoutAuthorNames());

            final Optional<SourceBook> book = client.fetchByTitleAuthor(HOBBIT_TITLE, HOBBIT_AUTHOR);

            assertThat(book).isEmpty();
        }

        @Test
        void shouldSkipAHitWithoutATitle() {
            stubGraphQl(bookSearch().withoutTitle());

            final Optional<SourceBook> book = client.fetchByIsbn(ABSOLUTE_BATMAN_ISBN);

            assertThat(book).get().extracting(SourceBook::title).isEqualTo(HOBBIT_TITLE);
        }
    }

    @Nested
    class SeriesFromOtherMemberships {

        @Test
        void shouldTakeTheCollectedSeriesWhenTheFeaturedOneIsAnIssueRun() {
            stubIssueRunSearchAndById(bookById());

            final Optional<SourceBook> book = client.fetchByIsbn(ABSOLUTE_BATMAN_ISBN);

            assertThat(book).get().satisfies(value -> {
                assertThat(value.seriesName()).isEqualTo(COLLECTED_SERIES);
                assertThat(value.seriesPosition()).isEqualTo(2);
                assertThat(value.series()).containsExactly(new SeriesEntry(COLLECTED_SERIES, 2));
            });
        }

        @Test
        void shouldDropTheIssueRunWhenNoOtherSeriesIsNumbered() {
            stubIssueRunSearchAndById(bookById().withOnlyFeaturedSeries());

            final Optional<SourceBook> book = client.fetchByIsbn(ABSOLUTE_BATMAN_ISBN);

            assertThat(book).get().satisfies(value -> {
                assertThat(value.seriesName()).isNull();
                assertThat(value.series()).isEmpty();
            });
        }

        @Test
        void shouldKeepTheBookWhenTheByIdLookupFindsNothing() {
            stubIssueRunSearchAndById(bookById().withoutBooks());

            final Optional<SourceBook> book = client.fetchByIsbn(ABSOLUTE_BATMAN_ISBN);

            assertThat(book).get().extracting(SourceBook::hardcoverId).isEqualTo(ABSOLUTE_BATMAN_ID);
        }

        @Test
        void shouldKeepTheIssueRunBookWhenItsIdIsNotNumeric() {
            stubGraphQl(issueRunSearch().withId(NON_NUMERIC_ID));

            final Optional<SourceBook> book = client.fetchByIsbn(ABSOLUTE_BATMAN_ISBN);

            assertThat(book).get().extracting(SourceBook::hardcoverId).isEqualTo(NON_NUMERIC_ID);
        }

        @Test
        void shouldMakeOneCallWhenTheFeaturedSeriesIsNotAnIssueRun() {
            stubGraphQl(bookSearch());

            client.fetchByIsbn("9780261103344");

            WIREMOCK.verify(1, postRequestedFor(urlPathEqualTo(GRAPHQL_PATH)));
        }

        private void stubIssueRunSearchAndById(final BookByIdJson byId) {
            stubGraphQl(SEARCH_OPERATION, issueRunSearch().withId(ABSOLUTE_BATMAN_ID));
            stubGraphQl(BY_ID_OPERATION, byId);
        }

        private static BookSearchJson issueRunSearch() {
            return bookSearch().withTitle("Absolute Batman, Vol. 2: Abomination")
                .withFeaturedSeries("Absolute Batman (2024) (Single Issues)");
        }
    }

    @Nested
    @DisplayName("fetch by id")
    class FetchById {

        @Test
        @DisplayName("a Hardcover id resolves the book record with its description")
        void byIdReturnsTheBook() {
            stubGraphQl(bookById());

            final Optional<SourceBook> result = client.fetchByHardcoverId(ABSOLUTE_BATMAN_ID);

            assertThat(result)
                .get()
                .satisfies(book -> {
                    assertThat(book.hardcoverId()).isEqualTo(ABSOLUTE_BATMAN_ID);
                    assertThat(book.description())
                        .isEqualTo("Batman faces the Abomination in the ruins of Gotham.");
                });
        }

        @Test
        void shouldAskHardcoverForItsFeaturedSeries() {
            stubGraphQl("featured_book_series {", bookById());

            final Optional<SourceBook> book = client.fetchByHardcoverId(ABSOLUTE_BATMAN_ID);

            assertThat(book).get().extracting(SourceBook::seriesName).isEqualTo(COLLECTED_SERIES);
        }

        @Test
        @DisplayName("a non-numeric id resolves to empty without a call")
        void byIdNonNumericIdIsEmpty() {
            stubStatus(HTTP_SERVER_ERROR);

            final Optional<SourceBook> book = client.fetchByHardcoverId(NON_NUMERIC_ID);

            assertThat(book).isEmpty();
        }
    }

    @Nested
    @DisplayName("error handling")
    class ErrorHandling {

        @Test
        @DisplayName("a 401 from an expired or revoked token resolves to empty")
        void unauthorizedIsEmpty() {
            stubStatus(HTTP_UNAUTHORIZED);

            final Optional<SourceBook> book = client.fetchByTitleAuthor(HOBBIT_TITLE, HOBBIT_AUTHOR);

            assertThat(book).isEmpty();
        }

        @Test
        @DisplayName("a 5xx propagates so a transient outage is not mistaken for 'book not found'")
        void serverErrorPropagates() {
            stubStatus(HTTP_SERVER_ERROR);

            assertThatThrownBy(() -> client.fetchByTitleAuthor(HOBBIT_TITLE, HOBBIT_AUTHOR))
                .isInstanceOf(WebClientResponseException.class);
        }
    }
}
