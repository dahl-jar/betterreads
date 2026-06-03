package com.betterreads.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.betterreads.catalog.entity.Book;
import com.betterreads.catalog.repository.BookRepository;
import com.betterreads.catalog.repository.PendingBookRepository;
import com.betterreads.catalog.service.CatalogSearchService;
import com.betterreads.catalog.service.PendingBookService;
import com.betterreads.common.util.LogSanitizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Runs the catalog search spine for the six series against the locally-running compose Postgres, so
 * the staged and promoted rows stay in {@code book} for inspection with {@code psql} after the JVM
 * exits.
 *
 * <p>Skipped unless {@code RUN_LOCAL_DB_VERIFICATION=1} and {@code GOOGLE_BOOKS_API_KEY} are set;
 * opt-in because it writes to a real database. Run with:
 * <pre>
 *   docker compose up -d db
 *   source .env &amp;&amp; RUN_LOCAL_DB_VERIFICATION=1 ./gradlew test \
 *     --tests '*CatalogPipelineLocalDbVerification*'
 *   docker exec -it betterreads-db psql -U betterreads -d betterreads -c \
 *     "SELECT series_name, series_position, title FROM book ORDER BY series_name, series_position;"
 * </pre>
 */
@SpringBootTest(properties = "betterreads.catalog.staging.poll-enabled=false")
@EnabledIfEnvironmentVariable(named = "RUN_LOCAL_DB_VERIFICATION", matches = "1")
@EnabledIfEnvironmentVariable(named = "GOOGLE_BOOKS_API_KEY", matches = ".+")
// PMD.ClassNamingConventions: not *Test on purpose, an opt-in manual DB verification, not a CI test
@SuppressWarnings("PMD.ClassNamingConventions")
class CatalogPipelineLocalDbVerification {

    private static final Logger LOG = LoggerFactory.getLogger(CatalogPipelineLocalDbVerification.class);

    private static final int FIRST_POSITION = 1;

    private static final String AUTHOR_QUERY = "Brandon Sanderson";

    private static final List<String> SERIES_QUERIES = List.of(
        "the wheel of time",
        "a song of ice and fire",
        "the lord of the rings",
        "dune",
        "the sandman",
        "watchmen");

    @Autowired
    private CatalogSearchService searchService;

    @Autowired
    private PendingBookService pendingBookService;

    @Autowired
    private PendingBookRepository pendingBooks;

    @Autowired
    private BookRepository books;

    @Test
    @DisplayName("the six series resolve through the spine into the local book table with real field values")
    void seriesLandInLocalDb() {
        pendingBooks.deleteAll();
        books.deleteAll();

        for (final String query : SERIES_QUERIES) {
            searchService.searchAndStage(query);
        }
        pendingBookService.promoteReady();

        final List<Book> stored = books.findAll();
        for (final Book book : stored) {
            LOG.info("catalog.localdb series={} pos={} {} | year={} rating={} cover={}",
                LogSanitizer.forLog(book.getSeriesName()),
                book.getSeriesPosition(),
                LogSanitizer.forLog(book.getTitle()),
                book.getFirstPublishYear(),
                book.getAverageRating(),
                book.getCoverUrl() != null);
        }

        assertThat(stored)
            .as("every stored book carries a title and a publication year, the show fields")
            .allSatisfy(book -> assertThat(book)
                .extracting(Book::getTitle, Book::getFirstPublishYear)
                .doesNotContainNull());

        assertThat(stored)
            .as("a series volume keeps the series name, ordered position, and rating from the spine")
            .anySatisfy(book -> assertThat(book)
                .satisfies(value -> {
                    assertThat(value.getSeriesName()).isEqualTo("The Wheel of Time");
                    assertThat(value.getSeriesPosition()).isEqualTo(FIRST_POSITION);
                    assertThat(value.getTitle()).isEqualTo("The Eye of the World");
                    assertThat(value.getAverageRating()).isNotNull();
                }));
    }

    @Test
    @DisplayName("an author search resolves Brandon Sanderson's books into the local book table, clean")
    void authorBooksLandInLocalDb() {
        pendingBooks.deleteAll();
        books.deleteAll();

        searchService.searchAuthorAndStage(AUTHOR_QUERY);
        pendingBookService.promoteReady();

        final List<Book> stored = books.findAll();
        for (final Book book : stored) {
            LOG.info("catalog.localdb.author {} | year={} rating={} isbn={} cover={}",
                LogSanitizer.forLog(book.getTitle()),
                book.getFirstPublishYear(),
                book.getAverageRating(),
                book.getIsbn() != null,
                book.getCoverUrl() != null);
        }

        assertThat(stored)
            .as("each of the author's books carries a title, year, and ISBN, with no edition or split noise")
            .isNotEmpty()
            .allSatisfy(book -> {
                assertThat(book.getTitle())
                    .doesNotContainIgnoringCase("audiobook", "deluxe edition", "boxed set")
                    .doesNotContainIgnoringCase(", part 1", "part one", "trilogy");
                assertThat(book.getFirstPublishYear()).isNotNull();
                assertThat(book.getIsbn()).isNotNull();
            });
        assertThat(stored)
            .as("a headline Sanderson novel resolves")
            .anySatisfy(book -> assertThat(book.getTitle()).isEqualTo("The Way of Kings"));
    }
}
