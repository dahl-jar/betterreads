package com.betterreads.clients.hardcoverauthor;

import com.betterreads.booksource.SourceAuthorWorks;
import com.betterreads.booksource.SourceBook;
import com.betterreads.clients.hardcover.HardcoverProperties;
import com.betterreads.clients.hardcover.HardcoverWebClientConfig;
import com.betterreads.clients.hardcover.HardcoverWireMock;
import static com.betterreads.clients.hardcover.AuthorSearchJson.authorSearch;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.list;

import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

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

    private static final String CO_AUTHORS = "Dan Wells, Brandon Sanderson";

    private static final String MISTBORN = "Mistborn: The Final Empire";

    private static final String COMPANION = "The World of Mistborn";

    private static final String ENGLISH = "English";

    private static final int MAX_BOOKS = 50;

    private static final String AUTHOR_WORKS = """
        {"data": {"authors": [{
          "name": "Brandon Sanderson",
          "contributions": [
            {"book": {"id": 1, "title": "Mistborn: The Final Empire", "users_count": 9706,
              "canonical_id": null,
              "book_series": [
                {"position": 5, "featured": false, "series": {"name": "The Cosmere"}},
                {"position": 1, "featured": true, "series": {"name": "Mistborn"}}],
              "default_physical_edition": {"language": {"language": "English"}}}},
            {"book": {"id": 2, "title": "Ostatnie Imperium", "users_count": 50,
              "canonical_id": 1,
              "default_physical_edition": {"language": {"language": "Polish"}}}},
            {"book": {"id": 3, "title": "Die Seele des Königs", "users_count": 40,
              "canonical_id": null,
              "default_physical_edition": {"language": {"language": "German"}}}},
            {"book": {"id": 4, "title": "Dangerous Women (Boxed Set)", "users_count": 30,
              "canonical_id": null,
              "default_physical_edition": {"language": {"language": "English"}}}},
            {"book": {"id": 6, "title": "Mistborn & The Stormlight Archive", "users_count": 9000,
              "canonical_id": null, "book_category_id": 1, "compilation": true,
              "default_physical_edition": {"language": {"language": "English"}}}},
            {"book": {"id": 5, "title": "The Way of Kings", "users_count": 7897,
              "canonical_id": null,
              "default_physical_edition": {"language": {"language": "English"}}}},
            {"book": {"id": 7, "title": "The World of Mistborn", "users_count": 20,
              "canonical_id": null, "book_category_id": 1,
              "book_series": [
                {"position": null, "featured": true, "series": {"name": "Mistborn"}}],
              "default_physical_edition": {"language": {"language": "English"}}}}
          ]
        }]}}
        """;

    @Autowired
    private HardcoverAuthorClientImpl client;

    private void stubSearchAndBooks() {
        stubGraphQl(SEARCH_MARKER, authorSearch());
        stubBooks();
    }

    private void stubBooks() {
        stubGraphQl(BOOKS_MARKER, AUTHOR_WORKS);
    }

    private static String worksIn(final String language, final int count) {
        final String contributions = IntStream.rangeClosed(1, count)
            .mapToObj(id -> ("{\"book\": {\"id\": %d, \"title\": \"Cosmere Novel %d\", "
                + "\"default_physical_edition\": {\"language\": {\"language\": \"%s\"}}}}")
                .formatted(id, id, language))
            .collect(Collectors.joining(","));
        return "{\"data\": {\"authors\": [{\"contributions\": [" + contributions + "]}]}}";
    }

    @Nested
    @DisplayName("candidate selection")
    class CandidateSelection {

        @Test
        @DisplayName("picks the author with the most books over search rank 0")
        void picksMostBooksAuthorNotRankZero() {
            stubSearchAndBooks();

            final Optional<SourceAuthorWorks> works = client.fetchAuthorWorks(QUERY);

            assertThat(works).get().extracting(SourceAuthorWorks::authorName).isEqualTo(QUERY);
        }

        @Test
        void shouldSkipAHitWithoutAName() {
            stubGraphQl(SEARCH_MARKER, authorSearch().withoutName());
            stubBooks();

            final Optional<SourceAuthorWorks> works = client.fetchAuthorWorks(CO_AUTHORS);

            assertThat(works).get().extracting(SourceAuthorWorks::authorName).isEqualTo(CO_AUTHORS);
        }

        @Test
        void shouldSkipAnAuthorWhoseNameIsNotInTheQuery() {
            stubSearchAndBooks();

            final Optional<SourceAuthorWorks> works = client.fetchAuthorWorks("Truth Matters");

            assertThat(works).isEmpty();
        }

        @Test
        void shouldPickAMatchingAuthorOverOneWithMoreBooks() {
            stubGraphQl(SEARCH_MARKER, authorSearch().withName("Phil Johnson"));
            stubBooks();

            final Optional<SourceAuthorWorks> works = client.fetchAuthorWorks(CO_AUTHORS);

            assertThat(works).get().extracting(SourceAuthorWorks::authorName).isEqualTo(CO_AUTHORS);
        }

        @Test
        void shouldFindAnAuthorNamedInALongerQuery() {
            stubSearchAndBooks();

            final Optional<SourceAuthorWorks> works = client.fetchAuthorWorks("mistborn brandon sanderson");

            assertThat(works).get().extracting(SourceAuthorWorks::authorName).isEqualTo(QUERY);
        }

        @Test
        void shouldResolveToEmptyWhenTheAuthorIdIsNotNumeric() {
            stubGraphQl(SEARCH_MARKER, authorSearch().withId("not-a-number"));
            stubBooks();

            final Optional<SourceAuthorWorks> works = client.fetchAuthorWorks(QUERY);

            assertThat(works).isEmpty();
        }

        @Test
        void shouldResolveToEmptyWhenTheWorksQueryFindsNoAuthor() {
            stubGraphQl(SEARCH_MARKER, authorSearch());
            stubGraphQl(BOOKS_MARKER, "{\"data\": {\"authors\": []}}");

            final Optional<SourceAuthorWorks> works = client.fetchAuthorWorks(QUERY);

            assertThat(works).isEmpty();
        }
    }

    @Nested
    @DisplayName("works filtering")
    class WorksFiltering {

        @Test
        @DisplayName("keeps the English canonical single books in reader order, dropping the rest")
        void keepsEnglishCanonicalSingleBooksInReaderOrder() {
            stubSearchAndBooks();

            final Optional<SourceAuthorWorks> works = client.fetchAuthorWorks(QUERY);

            assertThat(works).get()
                .extracting(SourceAuthorWorks::books, list(SourceBook.class))
                .extracting(SourceBook::title)
                .containsExactly(MISTBORN, "The Way of Kings", COMPANION);
        }

        @Test
        @DisplayName("a book carries its featured series name and position")
        void carriesFeaturedSeries() {
            stubSearchAndBooks();

            final Optional<SourceAuthorWorks> works = client.fetchAuthorWorks(QUERY);

            assertThat(works).get()
                .extracting(SourceAuthorWorks::books, list(SourceBook.class))
                .filteredOn(book -> MISTBORN.equals(book.title()))
                .first()
                .satisfies(book -> {
                    assertThat(book.seriesName())
                        .as("should take the featured series over the Cosmere meta-series")
                        .isEqualTo("Mistborn");
                    assertThat(book.seriesPosition()).isEqualTo(1);
                });
        }

        @Test
        void shouldResolveToEmptyWhenNoWorkIsInEnglish() {
            stubGraphQl(SEARCH_MARKER, authorSearch());
            stubGraphQl(BOOKS_MARKER, worksIn("Polish", 2));

            final Optional<SourceAuthorWorks> works = client.fetchAuthorWorks(QUERY);

            assertThat(works).isEmpty();
        }

        @Test
        void shouldKeepAtMostFiftyBooks() {
            stubGraphQl(SEARCH_MARKER, authorSearch());
            stubGraphQl(BOOKS_MARKER, worksIn(ENGLISH, MAX_BOOKS + 1));

            final Optional<SourceAuthorWorks> works = client.fetchAuthorWorks(QUERY);

            assertThat(works).get()
                .extracting(SourceAuthorWorks::books, list(SourceBook.class))
                .hasSize(MAX_BOOKS);
        }
    }
}
