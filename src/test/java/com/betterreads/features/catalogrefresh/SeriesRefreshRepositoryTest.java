package com.betterreads.features.catalogrefresh;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.betterreads.book.Book;
import com.betterreads.book.BookRepository;
import com.betterreads.book.BookUpsertService;
import com.betterreads.book.VerifiedMetadata;
import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.SourceBook;
import com.betterreads.testsupport.ContainerizedTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
class SeriesRefreshRepositoryTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String RED_RISING = "Red Rising Saga";

    private static final String SUN_EATER = "The Sun Eater";

    private static final String STORMLIGHT = "The Stormlight Archive";

    private static final int DAYS_IN_A_WEEK = 7;

    @Autowired
    private SeriesRefreshRepository seriesRefreshes;

    @Autowired
    private BookUpsertService upsert;

    @Autowired
    private BookRepository books;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clearCatalog() {
        books.deleteAll();
        jdbc.update("DELETE FROM series_refresh");
    }

    @Test
    void shouldReturnTheLongestWaitingSeriesFirst() {
        final OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        saveBookIn(RED_RISING);
        saveBookIn(SUN_EATER);
        saveBookIn(STORMLIGHT);
        seriesRefreshes.markRefreshed(RED_RISING, now.minusDays(DAYS_IN_A_WEEK));
        seriesRefreshes.markRefreshed(SUN_EATER, now.minusDays(1));
        seriesRefreshes.markRefreshed(RED_RISING, now);

        final List<String> due = seriesRefreshes.findSeriesDueForRefresh();

        assertThat(due).containsExactly(STORMLIGHT, SUN_EATER, RED_RISING);
    }

    @Test
    void shouldLeaveOutASeriesWhoseBooksAreAllVerified() {
        final Book verified = saveBookIn(RED_RISING);
        upsert.applyVerified(verified.getBookId(),
            new VerifiedMetadata(null, null, null, RED_RISING, 1.0, null, null, null), 1);
        saveBookIn(SUN_EATER);

        final List<String> due = seriesRefreshes.findSeriesDueForRefresh();

        assertThat(due).containsExactly(SUN_EATER);
    }

    private Book saveBookIn(final String series) {
        return upsert.upsertFromSource(SourceBook.builder(BookFieldSource.HARDCOVER)
            .hardcoverId("hc-" + series)
            .title("Volume one of " + series)
            .seriesName(series)
            .seriesPosition(1.0)
            .build());
    }
}
