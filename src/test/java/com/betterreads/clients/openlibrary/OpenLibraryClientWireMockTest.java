package com.betterreads.clients.openlibrary;

import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.SourceBook;
import static com.betterreads.clients.openlibrary.OpenLibrarySearchJson.HOBBIT_COVER_ID;
import static com.betterreads.clients.openlibrary.OpenLibrarySearchJson.HOBBIT_FIRST_PUBLISHED;
import static com.betterreads.clients.openlibrary.OpenLibrarySearchJson.HOBBIT_TITLE;
import static com.betterreads.clients.openlibrary.OpenLibrarySearchJson.HOBBIT_WORK_KEY;
import static com.betterreads.clients.openlibrary.OpenLibrarySearchJson.search;
import static com.betterreads.clients.openlibrary.OpenLibraryWorkJson.HOBBIT_DESCRIPTION;
import static com.betterreads.clients.openlibrary.OpenLibraryWorkJson.work;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
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
        OpenLibraryWebClientConfig.class,
        OpenLibraryClientImpl.class,
        OpenLibraryMapper.class
    },
    properties = "spring.main.web-application-type=none"
)
@EnableConfigurationProperties(OpenLibraryProperties.class)
class OpenLibraryClientWireMockTest extends OpenLibraryWireMock {

    private static final String HOBBIT_AUTHOR = "Tolkien";

    private static final String MISSING_TITLE = "Nope";

    private static final String MISSING_AUTHOR = "Nobody";

    private static final String GENRE_FANTASY = "fantasy";

    private static final String GENRE_FICTION = "fiction";

    private static final String GENRE_CLASSICS = "classics";

    @Autowired
    private OpenLibraryClientImpl client;

    @Nested
    @DisplayName("fetchByTitleAuthor: search then work detail")
    class FetchByTitleAuthor {

        @Test
        void mapsFullWork() {
            stubSearch(search());
            stubWork(HOBBIT_WORK_KEY, work());

            final Optional<SourceBook> result = client.fetchByTitleAuthor(HOBBIT_TITLE, HOBBIT_AUTHOR);

            assertThat(result)
                .isPresent()
                .get()
                .satisfies(book -> {
                    assertThat(book.source()).isEqualTo(BookFieldSource.OPEN_LIBRARY);
                    assertThat(book.openLibraryWorkKey()).isEqualTo(HOBBIT_WORK_KEY);
                    assertThat(book.publicationYear()).isEqualTo(HOBBIT_FIRST_PUBLISHED);
                    assertThat(book.coverUrl())
                        .isEqualTo("https://covers.openlibrary.org/b/id/" + HOBBIT_COVER_ID + "-L.jpg");
                    assertThat(book.description()).isEqualTo(HOBBIT_DESCRIPTION);
                    assertThat(book.rawSubjects())
                        .containsExactlyInAnyOrder(GENRE_FANTASY, GENRE_FICTION, GENRE_CLASSICS);
                });
        }

        @Test
        void picksCanonicalEarliestYear() {
            final int adaptationYear = 1940;
            final int reprintYear = 2021;
            final int canonicalYear = 1949;
            final int laterYear = 1984;
            final String title = "1984";
            final String canonicalKey = "OL_CANON";
            stubSearch(search().withoutHits()
                .withHit("OL_ADAPT", title + " (adaptation)", adaptationYear)
                .withHit("OL_REPRINT", title, reprintYear)
                .withHit(canonicalKey, title, canonicalYear)
                .withHit("OL_OTHER", title, laterYear));
            stubWork(canonicalKey, work());

            final Optional<SourceBook> result = client.fetchByTitleAuthor(title, "George Orwell");

            assertThat(result)
                .isPresent()
                .get()
                .satisfies(book -> {
                    assertThat(book.openLibraryWorkKey()).isEqualTo(canonicalKey);
                    assertThat(book.publicationYear()).isEqualTo(canonicalYear);
                });
        }

        @Test
        void prefixDriftRejected() {
            stubSearch(search().withoutHits().withHit("OL21213336W", "The Sandman - Overture"));

            final Optional<SourceBook> result = client.fetchByTitleAuthor("The Sandman", "Neil Gaiman");

            assertThat(result).isEmpty();
        }

        @Test
        void shouldKeepSearchHitWhenWorkIsMissing() {
            stubSearch(search());
            stubWorkStatus(HOBBIT_WORK_KEY, HTTP_NOT_FOUND);

            final Optional<SourceBook> result = client.fetchByTitleAuthor(HOBBIT_TITLE, HOBBIT_AUTHOR);

            assertThat(result).map(SourceBook::openLibraryWorkKey).contains(HOBBIT_WORK_KEY);
        }
    }

    @Nested
    @DisplayName("fetchByIsbn")
    class FetchByIsbn {

        @Test
        void mapsFirstHit() {
            stubSearch(search());
            stubWork(HOBBIT_WORK_KEY, work());
            final String isbn = "9780395282656";

            final Optional<SourceBook> result = client.fetchByIsbn(isbn);

            WIREMOCK.verify(getRequestedFor(urlPathEqualTo(SEARCH_PATH)).withQueryParam("q", equalTo("isbn:" + isbn)));
            assertThat(result)
                .isPresent()
                .get()
                .satisfies(book -> {
                    assertThat(book.openLibraryWorkKey()).isEqualTo(HOBBIT_WORK_KEY);
                    assertThat(book.title()).isEqualTo(HOBBIT_TITLE);
                });
        }
    }

    @Nested
    @DisplayName("search: multi-result discovery list")
    class Search {

        private static final int SEARCH_LIMIT = 10;

        @Test
        void mapsEveryHit() {
            final String query = "The Wheel of Time";
            final List<String> workKeys = List.of("OL1W", "OL2W", "OL3W");
            final OpenLibrarySearchJson search = search().withoutHits();
            workKeys.forEach(workKey -> search.withHit(workKey, query));
            stubSearch(search);

            final List<SourceBook> results = client.search(query, SEARCH_LIMIT);

            assertThat(results).extracting(SourceBook::openLibraryWorkKey).containsExactlyElementsOf(workKeys);
        }

        @Test
        void shouldDropHitsWithoutATitle() {
            stubSearch(search().withUntitledHit("OL9W"));

            final List<SourceBook> results = client.search(HOBBIT_TITLE, SEARCH_LIMIT);

            assertThat(results).extracting(SourceBook::openLibraryWorkKey).containsExactly(HOBBIT_WORK_KEY);
        }

    }

    @Nested
    @DisplayName("error handling")
    class ErrorHandling {

        @Test
        void notFoundIsEmpty() {
            stubSearchStatus(HTTP_NOT_FOUND);

            final Optional<SourceBook> result = client.fetchByTitleAuthor(MISSING_TITLE, MISSING_AUTHOR);

            assertThat(result).isEmpty();
        }

        @Test
        void shouldThrowWhenSearchReturns503() {
            stubSearchStatus(HTTP_SERVER_ERROR);

            assertThatThrownBy(() -> client.fetchByTitleAuthor(MISSING_TITLE, MISSING_AUTHOR))
                .isInstanceOf(WebClientResponseException.class);
        }

        @Test
        void shouldReturnEmptyWhenResponseHasNoDocs() {
            stubSearch(search().withoutDocs());

            final Optional<SourceBook> result = client.fetchByTitleAuthor(MISSING_TITLE, MISSING_AUTHOR);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("fetchByWorkKey")
    class FetchByWorkKey {

        @Test
        void fetchesWorkDirectly() {
            stubWork(HOBBIT_WORK_KEY, work());

            final Optional<SourceBook> result = client.fetchByWorkKey(HOBBIT_WORK_KEY);

            assertThat(result)
                .isPresent()
                .get()
                .satisfies(book -> assertThat(book.rawSubjects())
                    .containsExactlyInAnyOrder(GENRE_FANTASY, GENRE_FICTION, GENRE_CLASSICS));
        }
    }
}
