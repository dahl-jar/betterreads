package com.betterreads.integration.hardcover.client;

import static com.betterreads.integration.hardcover.AuthorSearchJson.authorSearch;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.betterreads.catalog.service.source.model.SourceAuthorWorks;
import com.betterreads.catalog.service.source.model.SourceBook;
import com.betterreads.integration.Fixtures;
import com.betterreads.integration.hardcover.HardcoverProperties;
import com.betterreads.integration.hardcover.HardcoverWebClientConfig;
import com.betterreads.integration.hardcover.HardcoverWireMock;
import com.betterreads.integration.hardcover.mapper.HardcoverAuthorMapper;
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
        HardcoverAuthorClientImpl.class,
        HardcoverAuthorMapper.class
    },
    properties = "spring.main.web-application-type=none"
)
@EnableConfigurationProperties(HardcoverProperties.class)
class HardcoverAuthorClientWireMockTest extends HardcoverWireMock {

    private static final String SEARCH_MARKER = "query_type: \\\"Author\\\"";

    private static final String BOOKS_MARKER = "contributions";

    private static final String QUERY = "Brandon Sanderson";

    private static final String MISTBORN = "Mistborn: The Final Empire";

    private static final String COMPANION = "The World of Mistborn";

    @Autowired
    private HardcoverAuthorClientImpl client;

    private void stubSearchAndBooks() {
        stubGraphQl(SEARCH_MARKER, authorSearch());
        stubGraphQl(BOOKS_MARKER, Fixtures.read("hardcover/author-works.json"));
    }

    @Nested
    @DisplayName("candidate selection")
    class CandidateSelection {

        @Test
        @DisplayName("picks the author with the most books, not search rank 0")
        void picksMostBooksAuthorNotRankZero() {
            stubSearchAndBooks();

            final SourceAuthorWorks works = client.fetchAuthorWorks(QUERY).orElseThrow();

            assertThat(works.authorName()).isEqualTo(QUERY);
        }

        @Test
        @DisplayName("no search hit resolves to empty")
        void noHitIsEmpty() {
            stubGraphQl(SEARCH_MARKER, authorSearch().withoutHits());

            assertThat(client.fetchAuthorWorks(QUERY)).isEmpty();
        }
    }

    @Nested
    @DisplayName("works filtering")
    class WorksFiltering {

        @Test
        @DisplayName("keeps the English canonical single books in reader order, dropping the rest")
        void keepsEnglishCanonicalSingleBooksInReaderOrder() {
            stubSearchAndBooks();

            final SourceAuthorWorks works = client.fetchAuthorWorks(QUERY).orElseThrow();

            assertThat(works.books())
                .extracting(SourceBook::title)
                .containsExactly(MISTBORN, "The Way of Kings", COMPANION);
        }

        @Test
        @DisplayName("a book carries its featured series name and position")
        void carriesFeaturedSeries() {
            stubSearchAndBooks();

            final SourceAuthorWorks works = client.fetchAuthorWorks(QUERY).orElseThrow();

            assertThat(works.books())
                .filteredOn(book -> MISTBORN.equals(book.title()))
                .first()
                .satisfies(book -> {
                    assertThat(book.seriesName())
                        .as("should take the featured series over the Cosmere meta-series")
                        .isEqualTo("Mistborn");
                    assertThat(book.seriesPosition()).isEqualTo(1);
                });
        }
    }

    @Nested
    @DisplayName("error handling")
    class ErrorHandling {

        @Test
        @DisplayName("a 401 from a rejected token resolves to empty")
        void unauthorizedIsEmpty() {
            stubStatus(HTTP_UNAUTHORIZED);

            assertThat(client.fetchAuthorWorks(QUERY)).isEmpty();
        }

        @Test
        @DisplayName("a 5xx propagates so an outage is not read as 'author not found'")
        void serverErrorPropagates() {
            stubStatus(HTTP_SERVER_ERROR);

            assertThatThrownBy(() -> client.fetchAuthorWorks(QUERY))
                .isInstanceOf(WebClientResponseException.class);
        }
    }
}
