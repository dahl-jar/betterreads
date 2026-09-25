package com.betterreads.integration.hardcover.client;

import static com.betterreads.integration.hardcover.BookByIdJson.bookById;
import static com.betterreads.integration.hardcover.BookSearchJson.bookSearch;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import com.betterreads.catalog.service.source.model.BookFieldSource;
import com.betterreads.catalog.service.source.model.SourceBook;
import com.betterreads.integration.hardcover.HardcoverProperties;
import com.betterreads.integration.hardcover.HardcoverWebClientConfig;
import com.betterreads.integration.hardcover.BookByIdJson;
import com.betterreads.integration.hardcover.BookSearchJson;
import com.betterreads.integration.hardcover.HardcoverWireMock;
import com.betterreads.integration.hardcover.mapper.HardcoverMapper;
import org.assertj.core.api.Assertions;
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

    @Autowired
    private HardcoverClientImpl client;

    @Nested
    @DisplayName("hit selection")
    class HitSelection {

        @Test
        @DisplayName("picks the highest-read hit over the one-rating stub at rank 0")
        void picksHighestReadHitNotRankZero() {
            stubGraphQl(bookSearch());

            assertThat(client.fetchByTitleAuthor(HOBBIT_TITLE, HOBBIT_AUTHOR))
                .isPresent()
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
            stubGraphQl(bookSearch().withTitle("A Completely Different Book").withAuthors("Someone Else"));

            assertThat(client.fetchByTitleAuthor(HOBBIT_TITLE, HOBBIT_AUTHOR))
                .as("should reject a hit whose title drifted from the query")
                .isEmpty();
        }

        @Test
        @DisplayName("a hit with the right title but a different author is rejected")
        void authorDriftRejected() {
            stubGraphQl(bookSearch().withAuthors("Some Other Author"));

            assertThat(client.fetchByTitleAuthor(HOBBIT_TITLE, HOBBIT_AUTHOR))
                .as("should reject a shared title under another author")
                .isEmpty();
        }
    }

    @Nested
    class AuthorCredits {

        @Test
        void shouldDropTheIllustratorCredit() {
            stubGraphQl(bookSearch());

            final Optional<SourceBook> book = client.fetchByTitleAuthor(HOBBIT_TITLE, HOBBIT_AUTHOR);

            assertThat(book).get().extracting(SourceBook::authorNames).isEqualTo(List.of(HOBBIT_AUTHOR));
        }
    }

    @Nested
    class SeriesFromOtherMemberships {

        @Test
        void shouldTakeTheCollectedSeriesWhenTheFeaturedOneIsAnIssueRun() {
            stubIssueRunSearchAndById(bookById());

            final Optional<SourceBook> book = client.fetchByIsbn(ABSOLUTE_BATMAN_ISBN);

            assertThat(book).get().satisfies(value -> {
                assertThat(value.seriesName()).isEqualTo("Absolute Batman (2024)");
                assertThat(value.seriesPosition()).isEqualTo(2);
            });
        }

        @Test
        void shouldDropTheIssueRunWhenNoOtherSeriesIsNumbered() {
            stubIssueRunSearchAndById(bookById().withOnlyFeaturedSeries());

            final Optional<SourceBook> book = client.fetchByIsbn(ABSOLUTE_BATMAN_ISBN);

            assertThat(book).get().extracting(SourceBook::seriesName).isNull();
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
            stubGraphQl("Search", issueRunSearch().withId(ABSOLUTE_BATMAN_ID));
            stubGraphQl("BookById", byId);
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

            assertThat(client.fetchByHardcoverId(ABSOLUTE_BATMAN_ID))
                .isPresent()
                .get()
                .satisfies(book -> {
                    assertThat(book.hardcoverId()).isEqualTo(ABSOLUTE_BATMAN_ID);
                    assertThat(book.description())
                        .isEqualTo("Batman faces the Abomination in the ruins of Gotham.");
                });
        }

        @Test
        @DisplayName("an id Hardcover does not know resolves to empty")
        void byIdUnknownIdIsEmpty() {
            stubGraphQl(bookById().withoutBooks());

            assertThat(client.fetchByHardcoverId("999999999")).isEmpty();
        }

        @Test
        @DisplayName("a non-numeric id resolves to empty without a call")
        void byIdNonNumericIdIsEmpty() {
            stubStatus(HTTP_SERVER_ERROR);

            assertThat(client.fetchByHardcoverId(NON_NUMERIC_ID)).isEmpty();
        }
    }

    @Nested
    @DisplayName("error handling")
    class ErrorHandling {

        @Test
        @DisplayName("a 401 from an expired or revoked token resolves to empty")
        void unauthorizedIsEmpty() {
            stubStatus(HTTP_UNAUTHORIZED);

            assertThat(client.fetchByTitleAuthor(HOBBIT_TITLE, HOBBIT_AUTHOR)).isEmpty();
        }

        @Test
        @DisplayName("a 5xx propagates so a transient outage is not mistaken for 'book not found'")
        void serverErrorPropagates() {
            stubStatus(HTTP_SERVER_ERROR);

            Assertions.assertThatThrownBy(() -> client.fetchByTitleAuthor(HOBBIT_TITLE, HOBBIT_AUTHOR))
                .isInstanceOf(WebClientResponseException.class);
        }
    }
}
