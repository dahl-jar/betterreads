package com.betterreads.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import com.betterreads.catalog.entity.Book;
import com.betterreads.catalog.repository.BookRepository;
import com.betterreads.catalog.repository.PendingBookRepository;
import com.betterreads.common.util.LogSanitizer;
import com.betterreads.catalog.service.BookSourceClient;
import com.betterreads.catalog.service.PendingBookService;
import com.betterreads.catalog.service.SingleBookFilter;
import com.betterreads.catalog.service.SourceBook;
import com.betterreads.catalog.service.SourceMerger;
import com.betterreads.integration.openlibrary.OpenLibraryClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Runs the whole catalog pipeline for the six-book slate against the locally-running compose
 * Postgres, so the merged rows stay in {@code book} for inspection with {@code psql} after the JVM
 * exits. Unlike the Testcontainers E2E, this writes to the real long-lived database.
 *
 * <p>Skipped unless {@code RUN_LOCAL_DB_VERIFICATION=1} AND {@code GOOGLE_BOOKS_API_KEY} are set;
 * opt-in because it writes to a real database. Run with:
 * <pre>
 *   docker compose up -d db
 *   source .env &amp;&amp; RUN_LOCAL_DB_VERIFICATION=1 ./gradlew test \
 *     --tests '*CatalogPipelineLocalDbVerification*'
 *   docker exec -it betterreads-db psql -U betterreads -d betterreads -c \
 *     "SELECT title, first_publish_year, average_rating, page_count FROM book ORDER BY title;"
 * </pre>
 */
@SpringBootTest(properties = "betterreads.catalog.staging.poll-enabled=false")
@EnabledIfEnvironmentVariable(named = "RUN_LOCAL_DB_VERIFICATION", matches = "1")
@EnabledIfEnvironmentVariable(named = "GOOGLE_BOOKS_API_KEY", matches = ".+")
// PMD.ClassNamingConventions: not *Test on purpose, an opt-in manual DB verification, not a CI test
@SuppressWarnings("PMD.ClassNamingConventions")
class CatalogPipelineLocalDbVerification {

    private static final Logger LOG = LoggerFactory.getLogger(CatalogPipelineLocalDbVerification.class);

    private static final int MAIN_SERIES_BOOKS = 14;

    private static final List<Slate> SLATE = List.of(
        new Slate("The Eye of the World", "Robert Jordan"),
        new Slate("A Clash of Kings", "George R. R. Martin"),
        new Slate("The Hobbit", "J.R.R. Tolkien"),
        new Slate("Dune", "Frank Herbert"),
        new Slate("The Sandman", "Neil Gaiman"),
        new Slate("Watchmen", "Alan Moore"));

    @Autowired
    private List<BookSourceClient> sourceClients;

    @Autowired
    private OpenLibraryClient openLibraryClient;

    @Autowired
    private SourceMerger merger;

    @Autowired
    private PendingBookService pendingBookService;

    @Autowired
    private PendingBookRepository pendingBooks;

    @Autowired
    private BookRepository books;

    @Test
    @DisplayName("the slate flows from live sources into the local book table, with the merged fields logged per book")
    void slateLandsInLocalDb() {
        pendingBooks.deleteAll();
        books.deleteAll();

        for (final Slate slate : SLATE) {
            stageFromLiveSources(slate);
        }
        pendingBookService.promoteReady();

        for (final Book book : books.findAll()) {
            LOG.info("catalog.localdb {} | year={} rating={} pages={} cover={} isbn={}",
                LogSanitizer.forLog(book.getTitle()),
                book.getFirstPublishYear(),
                book.getAverageRating(),
                book.getPageCount(),
                book.getCoverUrl() != null,
                book.getIsbn() != null);
        }
        assertThat(books.count())
            .as("at least one slate book must reach the local catalog")
            .isGreaterThanOrEqualTo(1L);
    }

    private void stageFromLiveSources(final Slate slate) {
        final List<SourceBook> sources = new ArrayList<>();
        for (final BookSourceClient client : sourceClients) {
            client.fetchByTitleAuthor(slate.title(), slate.author()).ifPresent(sources::add);
        }
        if (sources.isEmpty()) {
            LOG.warn("catalog.localdb no source returned {}", LogSanitizer.forLog(slate.title()));
            return;
        }
        pendingBookService.stage(merger.merge(sources));
    }

    @Test
    @DisplayName("a Wheel of Time search keeps the single novels and drops the collections")
    void wheelOfTimeSearchReturnsTheSeries() {
        final List<SourceBook> hits = openLibraryClient.search("The Wheel of Time Robert Jordan", 30);
        final List<SourceBook> singleBooks = hits.stream()
            .filter(hit -> hit.title() != null && SingleBookFilter.isSingleBook(hit.title()))
            .toList();

        for (final SourceBook hit : singleBooks) {
            LOG.info("catalog.series {}", LogSanitizer.forLog(hit.title()));
        }
        LOG.info("catalog.series.summary {} hits, {} single books after filtering",
            hits.size(), singleBooks.size());
        assertThat(singleBooks)
            .as("the fourteen main novels are distinct single books, kept after filtering collections")
            .hasSizeGreaterThanOrEqualTo(MAIN_SERIES_BOOKS);
        assertThat(singleBooks)
            .extracting(SourceBook::title)
            .anySatisfy(title -> assertThat(title).contains("Eye of the World"))
            .noneSatisfy(title -> assertThat(title).contains("Boxed Set"));
    }

    private record Slate(String title, String author) {
    }
}
