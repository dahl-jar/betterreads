package com.betterreads.features.bookstaging;

import com.betterreads.book.BookRepository;
import com.betterreads.bookmerge.SourceMerger;
import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.SourceBook;
import com.betterreads.pendingbook.PendingBook;
import com.betterreads.pendingbook.PendingBookRepository;
import com.betterreads.testsupport.ContainerizedTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static com.betterreads.features.bookstaging.DuneBooks.ISBN;
import static com.betterreads.features.bookstaging.DuneBooks.SERIES_POSITION;
import static com.betterreads.features.bookstaging.NoNetworkSources.HARDCOVER_RESPONSE;
import static com.betterreads.features.bookstaging.NoNetworkSources.OPEN_LIBRARY_RESPONSE;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
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
class PendingBookFirstTitleIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String FIRST_TITLE = "Hollow Throne";

    private static final String STORED_TITLE = "Hollow Throne: The Life of a Queen";

    private static final String RETRY_TITLE = "Hollow Throne: Book One of the Wars";

    private static final String RETRY_SERIES = "Wars";

    private static final Duration RETRY_WAIT = Duration.ofMinutes(16);

    @Autowired
    private PendingBookService pendingBookService;

    @Autowired
    private BookStager stager;

    @Autowired
    private SourceMerger merger;

    @Autowired
    private PendingBookRepository pendingBooks;

    @Autowired
    private BookRepository books;

    @BeforeEach
    void clearCatalog() {
        pendingBooks.deleteAll();
        books.deleteAll();
        NoNetworkSources.reset();
    }

    @Test
    void shouldKeepHitFirstAnsweringOnRetryWithAnotherSubtitle() {
        stageSeed(FIRST_TITLE);
        HARDCOVER_RESPONSE.set(retryHit());

        retry();

        assertThat(pendingBooks.findByIsbn13(ISBN))
            .get()
            .satisfies(row -> assertThat(row.getSeriesName()).isEqualTo(RETRY_SERIES));
    }

    @Test
    void shouldKeepFirstTitleOnRetry() {
        stageSeed(FIRST_TITLE);
        HARDCOVER_RESPONSE.set(retryHit());

        retry();

        assertThat(pendingBooks.findByIsbn13(ISBN))
            .get()
            .satisfies(row -> assertThat(row.getFirstTitle()).isEqualTo(FIRST_TITLE));
    }

    @Test
    void shouldKeepFirstTitleWhenStagedAgain() {
        pendingBookService.stage(merger.merge(null, List.of(DuneBooks.completeDune())), FIRST_TITLE);

        pendingBookService.stage(merger.merge(null, List.of(DuneBooks.completeDune())), RETRY_TITLE);

        assertThat(pendingBooks.findByIsbn13(ISBN))
            .get()
            .satisfies(row -> assertThat(row.getFirstTitle()).isEqualTo(FIRST_TITLE));
    }

    @Test
    void shouldCompareWithStoredTitleWhenSeedHadNoTitle() {
        stageSeed(null);
        HARDCOVER_RESPONSE.set(retryHit());

        retry();

        assertThat(pendingBooks.findByIsbn13(ISBN))
            .get()
            .satisfies(row -> {
                assertThat(row.getFirstTitle()).isNull();
                assertThat(row.getSeriesName()).isNull();
            });
    }

    private void stageSeed(final @Nullable String seedTitle) {
        OPEN_LIBRARY_RESPONSE.set(SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
            .isbn13(ISBN)
            .title(STORED_TITLE)
            .build());
        stager.stage(SourceBook.builder(BookFieldSource.WIKIDATA).isbn13(ISBN).title(seedTitle).build());
    }

    private static SourceBook retryHit() {
        return SourceBook.builder(BookFieldSource.HARDCOVER)
            .isbn13(ISBN)
            .title(RETRY_TITLE)
            .seriesName(RETRY_SERIES)
            .seriesPosition(SERIES_POSITION)
            .build();
    }

    private void retry() {
        final PendingBook row = pendingBooks.findByIsbn13(ISBN).orElseThrow();
        row.setLastAttemptAt(OffsetDateTime.now(ZoneOffset.UTC).minus(RETRY_WAIT));
        pendingBooks.save(row);
        pendingBookService.promoteReady();
    }
}
