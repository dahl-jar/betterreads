package com.betterreads.catalog.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Objects;

import com.betterreads.catalog.entity.Author;
import com.betterreads.catalog.entity.Book;
import com.betterreads.catalog.entity.PendingBook;
import com.betterreads.catalog.repository.BookRepository;
import com.betterreads.catalog.repository.PendingBookRepository;
import com.betterreads.catalog.service.pipeline.CatalogSearchService;
import com.betterreads.catalog.service.pipeline.PendingBookService;
import com.betterreads.catalog.service.pipeline.SourceCollector;
import com.betterreads.catalog.service.source.model.BookFieldSource;
import com.betterreads.catalog.service.source.model.MergedBook;
import com.betterreads.catalog.service.source.model.SourceAuthor;
import com.betterreads.catalog.service.source.model.SourceBook;
import com.betterreads.common.util.LogSanitizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Persists live catalog search results in local Postgres for operator inspection. */
@SpringBootTest(properties = "betterreads.catalog.staging.poll-enabled=false")
@EnabledIfEnvironmentVariable(named = "RUN_LOCAL_DB_VERIFICATION", matches = "1")
@EnabledIfEnvironmentVariable(named = "GOOGLE_BOOKS_API_KEY", matches = ".+")
// PMD.ClassNamingConventions: operator-run database verification without a Test suffix
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
    @DisplayName("the six series persist with their display fields")
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
            .as("a series volume keeps its name, ordered position, and rating")
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

    @Test
    @DisplayName("an author search stores Brandon Sanderson's books without edition noise")
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

    private void stageAndPromote(final SourceBook seed) {
        final MergedBook merged = sourceCollector.collectFor(seed);
        final String dedupKey = Objects.requireNonNull(
            merged.book().dedupKey(), "merged seed has a dedup key");
        pendingBookService.stage(merged);
        pendingBookService.promoteNow(dedupKey, merged);
    }
}
