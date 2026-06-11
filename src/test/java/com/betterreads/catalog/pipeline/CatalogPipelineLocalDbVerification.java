package com.betterreads.catalog.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.betterreads.catalog.entity.Author;
import com.betterreads.catalog.entity.Book;
import com.betterreads.catalog.entity.PendingBook;
import com.betterreads.catalog.repository.BookRepository;
import com.betterreads.catalog.repository.PendingBookRepository;
import com.betterreads.catalog.service.pipeline.CatalogSearchService;
import com.betterreads.catalog.service.pipeline.PendingBookService;
import com.betterreads.catalog.service.pipeline.SourceCollector;
import com.betterreads.catalog.service.source.BookFieldSource;
import com.betterreads.catalog.service.source.MergedBook;
import com.betterreads.catalog.service.source.SourceAuthor;
import com.betterreads.catalog.service.source.SourceBook;
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

    private static final String NOVEL_TITLE = "The Eye of the World";

    private static final String NOVEL_HARDCOVER_ID = "77104";

    private static final String JORDAN = "Robert Jordan";

    private static final int NOVEL_YEAR = 1990;

    private static final int COMPANION_YEAR = 1997;

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
    private SourceCollector sourceCollector;

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
                    assertThat(value.getTitle()).isEqualTo(NOVEL_TITLE);
                    assertThat(value.getAverageRating()).isNotNull();
                }));
    }

    @Test
    @DisplayName("staging the companion guide leaves the already-promoted novel untouched")
    void companionGuideDoesNotOverwriteTheNovel() {
        pendingBooks.deleteAll();
        books.deleteAll();

        stageAndPromote(SourceBook.builder(BookFieldSource.HARDCOVER)
            .hardcoverId(NOVEL_HARDCOVER_ID)
            .title(NOVEL_TITLE)
            .authors(SourceAuthor.ofNames(List.of(JORDAN)))
            .publicationYear(NOVEL_YEAR)
            .description("The Wheel of Time turns and Ages come and pass, leaving memories that "
                + "become legend. Legend fades to myth, and even myth is long forgotten when the "
                + "Age that gave it birth returns again.")
            .coverUrl("https://assets.hardcover.app/edition/30621010/97e433a0.png")
            .build());

        stageAndPromote(SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
            .openLibraryWorkKey("OL1946690W")
            .title("The world of Robert Jordan's the wheel of time")
            .authors(SourceAuthor.ofNames(List.of(JORDAN, "Teresa Patterson")))
            .publicationYear(COMPANION_YEAR)
            .build());

        final Book novel = books.findByHardcoverId(NOVEL_HARDCOVER_ID).orElseThrow();
        assertThat(novel.getTitle())
            .as("the novel keeps its title after the companion stages")
            .isEqualTo(NOVEL_TITLE);
        assertThat(novel.getAuthors())
            .extracting(Author::getName)
            .as("the novel keeps its sole author")
            .containsExactly(JORDAN);

        final List<String> stagedKeys = pendingBooks.findAll().stream()
            .map(PendingBook::getDedupKey)
            .toList();
        assertThat(stagedKeys)
            .as("the novel and the companion hold separate staging rows")
            .hasSize(2)
            .doesNotHaveDuplicates();
    }

    /** Stages and promotes one seed the way {@link CatalogSearchService} does for a search hit. */
    private void stageAndPromote(final SourceBook seed) {
        final MergedBook merged = sourceCollector.collectFor(seed);
        final String dedupKey = merged.book().dedupKey();
        pendingBookService.stage(merged);
        pendingBookService.promoteNow(dedupKey, merged);
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
