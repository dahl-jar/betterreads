package com.betterreads.integration.openlibrary.client;

import static com.betterreads.integration.openlibrary.OpenLibrarySearchJson.search;
import static com.betterreads.integration.openlibrary.OpenLibraryWorkJson.work;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import com.betterreads.catalog.service.source.model.BookFieldSource;
import com.betterreads.catalog.service.source.model.SourceBook;
import com.betterreads.integration.openlibrary.OpenLibraryProperties;
import com.betterreads.integration.openlibrary.OpenLibrarySearchJson;
import com.betterreads.integration.openlibrary.OpenLibraryWebClientConfig;
import com.betterreads.integration.openlibrary.OpenLibraryWireMock;
import com.betterreads.integration.openlibrary.mapper.OpenLibraryMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

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

    private static final int HOBBIT_FIRST_PUBLISHED = 1937;

    private static final String HOBBIT_WORK_KEY = "OL27482W";

    private static final String HOBBIT_TITLE = "The Hobbit";

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
                        .isEqualTo("https://covers.openlibrary.org/b/id/14627509-L.jpg");
                    assertThat(book.description()).isEqualTo("A tale of high adventure.");
                    assertThat(book.rawSubjects())
                        .containsExactlyInAnyOrder(GENRE_FANTASY, GENRE_FICTION, GENRE_CLASSICS);
                });
        }

        @ParameterizedTest
        @CsvSource("2003, 2021, 1949, 1984")
        void picksCanonicalEarliestYear(
            final int adaptationYear, final int reprintYear, final int canonicalYear, final int laterYear) {
            final String title = "1984";
            final String canonicalKey = "OL_CANON";
            stubSearch(search().withoutHits()
                .withHit("OL_ADAPT", title + " (adaptation)", adaptationYear)
                .withHit("OL_REPRINT", title, reprintYear)
                .withHit(canonicalKey, title, canonicalYear)
                .withHit("OL_OTHER", title, laterYear));
            stubWork(canonicalKey, work().withKey(canonicalKey));

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
        void noDocsIsEmpty() {
            stubSearch(search().withoutHits());

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
