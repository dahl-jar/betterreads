package com.betterreads.features.bookstaging;

import com.betterreads.book.BookRepository;
import com.betterreads.bookmerge.SourceMerger;
import com.betterreads.booksource.MergedBook;
import com.betterreads.pendingbook.PendingBook;
import com.betterreads.pendingbook.PendingBookMapper;
import com.betterreads.pendingbook.PendingBookRepository;
import com.betterreads.pendingbook.PendingBookStatus;
import com.betterreads.testsupport.ContainerizedTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static com.betterreads.features.bookstaging.DuneBooks.AUTHOR;
import static com.betterreads.features.bookstaging.DuneBooks.GENRE;
import static com.betterreads.features.bookstaging.DuneBooks.ISBN;
import static com.betterreads.features.bookstaging.DuneBooks.OL_KEY;
import static com.betterreads.features.bookstaging.DuneBooks.SECOND_EDITION_ISBN;
import static com.betterreads.features.bookstaging.DuneBooks.SEQUEL_ISBN;
import static com.betterreads.features.bookstaging.DuneBooks.SERIES_POSITION;
import static com.betterreads.features.bookstaging.DuneBooks.TITLE;
import static com.betterreads.features.bookstaging.NoNetworkSources.HARDCOVER_RESPONSE;
import static com.betterreads.features.bookstaging.NoNetworkSources.OPEN_LIBRARY_RESPONSE;
import static com.betterreads.features.bookstaging.NoNetworkSources.WIKIDATA_RESPONSE;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "betterreads.catalog.staging.poll-enabled=false"
})
@Import(NoNetworkSources.class)
class PendingBookServiceIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String STALE_AUTHOR = "Stale Author";

    private static final String AUTHOR_NAME_FIELD = "name";

    @Autowired
    private PendingBookService pendingBookService;

    @Autowired
    private SourceMerger merger;

    @Autowired
    private PendingBookRepository pendingBooks;

    @Autowired
    private BookRepository books;

    @Autowired
    private SourceCollector sourceCollector;

    @Autowired
    private PendingBookMapper pendingBookMapper;

    @BeforeEach
    void clearCatalog() {
        pendingBooks.deleteAll();
        books.deleteAll();
        NoNetworkSources.reset();
    }

    @Test
    @DisplayName("a candidate with every required field is promoted into book")
    void readyCandidateIsPromoted() {
        pendingBookService.stage(merger.merge(null, List.of(DuneBooks.completeDune())));

        pendingBookService.promoteReady();

        assertThat(books.findByOpenLibraryWorkKey(OL_KEY))
            .as("a complete candidate must land in book")
            .isPresent();
        assertThat(pendingBooks.findByIsbn13(ISBN))
            .get()
            .satisfies(row -> assertThat(row.getStatus()).isEqualTo(PendingBookStatus.PROMOTED));
    }

    @Test
    @DisplayName("an incomplete candidate stays staged and never reaches book")
    void incompleteCandidateStaysStaged() {
        pendingBookService.stage(merger.merge(null, List.of(DuneBooks.sparseDune())));

        pendingBookService.promoteReady();

        assertThat(books.count())
            .as("a candidate missing required fields must not be promoted")
            .isZero();
        assertThat(pendingBooks.findByIsbn13(ISBN))
            .get()
            .satisfies(row -> assertThat(row.getStatus()).isEqualTo(PendingBookStatus.PENDING));
    }

    @Test
    @DisplayName("promotion takes the series and rating from the Hardcover source the collect fetches")
    void promotionTakesSeriesAndRatingFromHardcover() {
        HARDCOVER_RESPONSE.set(DuneBooks.hardcoverDune());
        pendingBookService.stage(merger.merge(null, List.of(DuneBooks.completeDune())));

        pendingBookService.promoteReady();

        assertThat(books.findByOpenLibraryWorkKey(OL_KEY))
            .as("Hardcover supplies the series and rating on the collect, so they reach the book")
            .get()
            .satisfies(book -> {
                assertThat(book.getSeriesName()).isEqualTo(TITLE);
                assertThat(book.getSeriesPosition()).isEqualTo(SERIES_POSITION);
                assertThat(book.getAverageRating()).isNotNull();
            });
    }

    @Test
    void shouldKeepSeriesAfterAHardcoverMiss() {
        HARDCOVER_RESPONSE.set(DuneBooks.hardcoverDune());
        pendingBookService.stage(merger.merge(null, List.of(DuneBooks.completeDune())));
        pendingBookService.promoteReady();
        HARDCOVER_RESPONSE.set(DuneBooks.hardcoverDune().toBuilder().isbn13(SEQUEL_ISBN).build());
        WIKIDATA_RESPONSE.set(DuneBooks.wikidataDuneChronicles());

        rePromote();

        assertThat(books.findByOpenLibraryWorkKey(OL_KEY))
            .get()
            .satisfies(book -> assertThat(book.getSeriesName()).isEqualTo(TITLE));
    }

    @Test
    @DisplayName("re-promotion keeps a real series when Hardcover fails")
    void rePromotionKeepsSeriesWhenHardcoverFails() {
        HARDCOVER_RESPONSE.set(DuneBooks.hardcoverDune());
        pendingBookService.stage(merger.merge(null, List.of(DuneBooks.completeDune())));
        pendingBookService.promoteReady();

        HARDCOVER_RESPONSE.set(null);
        rePromote();

        assertThat(books.findByOpenLibraryWorkKey(OL_KEY))
            .as("Hardcover did not resolve, so the existing series is kept")
            .get()
            .satisfies(book -> {
                assertThat(book.getSeriesName()).isEqualTo(TITLE);
                assertThat(book.getSeriesPosition()).isEqualTo(SERIES_POSITION);
            });
    }

    @Test
    @DisplayName("re-promotion corrects a stale staged author from a live OpenLibrary fetch")
    void rePromotionDropsStaleAuthor() {
        pendingBookService.stage(merger.merge(null, List.of(DuneBooks.duneBy(AUTHOR, STALE_AUTHOR))));
        pendingBookService.promoteReady();

        OPEN_LIBRARY_RESPONSE.set(DuneBooks.openLibraryDune());
        rePromote();

        assertThat(books.findByOpenLibraryWorkKey(OL_KEY))
            .as("the live OpenLibrary fetch names one author, so the stale staged one is removed")
            .get()
            .satisfies(book -> assertThat(book.getAuthors())
                .extracting(AUTHOR_NAME_FIELD)
                .containsExactly(AUTHOR));
    }

    @Test
    @DisplayName("a re-promotion with every live source down keeps the staged book intact")
    void allSourcesDownRePromotionKeepsStagedBook() {
        pendingBookService.stage(merger.merge(null, List.of(DuneBooks.completeDune())));
        pendingBookService.promoteReady();

        rePromote();

        assertThat(books.findByOpenLibraryWorkKey(OL_KEY))
            .as("no live source resolved, so the staged values still promote the book unchanged")
            .get()
            .satisfies(book -> {
                assertThat(book.getTitle()).isEqualTo(TITLE);
                assertThat(book.getAuthors())
                    .extracting(AUTHOR_NAME_FIELD)
                    .containsExactly(AUTHOR);
            });
    }

    @Test
    @DisplayName("a candidate colliding with a promoted work is retired and the poll reaches the rest")
    void collidingCandidateIsRetiredAndPollContinues() {
        pendingBookService.stage(merger.merge(null, List.of(DuneBooks.completeDune())));
        pendingBookService.promoteReady();
        pendingBookService.stage(merger.merge(null, List.of(DuneBooks.secondEditionDune())));
        pendingBookService.stage(merger.merge(null, List.of(DuneBooks.duneMessiah())));
        OPEN_LIBRARY_RESPONSE.set(DuneBooks.collidingSecondEdition());

        pendingBookService.promoteReady();

        assertThat(pendingBooks.findByDedupKey(SECOND_EDITION_ISBN))
            .as("the second edition resolves a work key another row owns, and no retry changes that")
            .get()
            .satisfies(row -> assertThat(row.getStatus()).isEqualTo(PendingBookStatus.DUPLICATE));
        assertThat(books.findByDedupKey(SEQUEL_ISBN))
            .as("the candidate behind the colliding one must still be promoted")
            .isPresent();
    }

    @Test
    @DisplayName("an attempted candidate is not collected again until the retry window passes")
    void attemptedCandidateWaitsForTheRetryWindow() {
        pendingBookService.stage(merger.merge(null, List.of(DuneBooks.sparseDune())));
        pendingBookService.promoteReady();

        OPEN_LIBRARY_RESPONSE.set(DuneBooks.openLibraryCompleteDune());
        pendingBookService.promoteReady();

        assertThat(pendingBooks.findByIsbn13(ISBN))
            .as("the candidate was attempted moments ago, so the poll must skip it this cycle")
            .get()
            .satisfies(row -> assertThat(row.getStatus()).isEqualTo(PendingBookStatus.PENDING));
    }

    @Test
    @DisplayName("a two-author book comes back with each subject once")
    void fetchReturnsEachSubjectOnceForTwoAuthorBook() {
        pendingBookService.stage(merger.merge(null, List.of(DuneBooks.duneBy(AUTHOR, STALE_AUTHOR))));
        pendingBookService.promoteReady();

        assertThat(books.findByDedupKey(ISBN))
            .as("the read fetch joins authors and subjects, and the join must not repeat subject rows")
            .get()
            .satisfies(book -> assertThat(book.getSubjects())
                .extracting("subject")
                .containsExactly(GENRE));
    }

    /** The poll only picks PENDING rows, so a promoted book is re-promoted the way the nightly refresh does it. */
    private void rePromote() {
        final PendingBook row = pendingBooks.findByIsbn13(ISBN).orElseThrow();
        final MergedBook collected = sourceCollector.collectFor(pendingBookMapper.toSourceBook(row));
        pendingBookService.promoteNow(ISBN, collected);
    }
}
