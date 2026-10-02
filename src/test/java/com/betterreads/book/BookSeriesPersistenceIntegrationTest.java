package com.betterreads.book;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;

import com.betterreads.bookindex.BookIndexView;
import com.betterreads.bookindex.BookIndexViewReader;
import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.SeriesEntry;
import com.betterreads.booksource.SourceAuthor;
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
class BookSeriesPersistenceIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String HARDCOVER_ID = "hc-1";

    private static final SeriesEntry STORMLIGHT = new SeriesEntry("The Stormlight Archive", 2);

    private static final SeriesEntry COSMERE = new SeriesEntry("The Cosmere", 12);

    private static final String DARROW = "Darrow";

    private static final String MUSTANG = "Mustang";

    private static final String SERIES_ROWS_SQL =
        "SELECT series_name FROM book_series WHERE book_id = ? ORDER BY ordinal";

    private static final String UPDATED_AT_SQL = "SELECT updated_at FROM book WHERE book_id = ?";

    @Autowired
    private BookUpsertService bookUpsertService;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private BookIndexViewReader indexViews;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clearCatalog() {
        bookRepository.deleteAll();
    }

    private static SourceBook wordsOfRadiance(final List<SeriesEntry> series) {
        return SourceBook.builder(BookFieldSource.HARDCOVER)
            .hardcoverId(HARDCOVER_ID)
            .title("Words of Radiance")
            .authors(SourceAuthor.ofNames(List.of(DARROW, MUSTANG)))
            .series(series)
            .build();
    }

    @Test
    void shouldStoreEverySeriesRowInOrder() {
        final Book book = bookUpsertService.upsertFromSource(wordsOfRadiance(List.of(STORMLIGHT, COSMERE)));

        final List<String> rows = jdbc.queryForList(SERIES_ROWS_SQL, String.class, book.getBookId());

        assertThat(rows).containsExactly(STORMLIGHT.name(), COSMERE.name());
    }

    @Test
    void shouldChangeTheUpdateTimeWhenASecondSeriesIsAdded() {
        final Book book = bookUpsertService.upsertFromSource(wordsOfRadiance(List.of(STORMLIGHT)));
        final OffsetDateTime before = updatedAt(book);

        bookUpsertService.upsertFromSource(wordsOfRadiance(List.of(STORMLIGHT, COSMERE)));

        assertThat(updatedAt(book)).isAfter(before);
    }

    @Test
    void shouldKeepTheUpdateTimeWhenTheSeriesIsUnchanged() {
        final Book book = bookUpsertService.upsertFromSource(wordsOfRadiance(List.of(STORMLIGHT, COSMERE)));
        final OffsetDateTime before = updatedAt(book);

        bookUpsertService.upsertFromSource(wordsOfRadiance(List.of(STORMLIGHT, COSMERE)));

        assertThat(updatedAt(book)).isEqualTo(before);
    }

    @Test
    void shouldReadEverySeriesIntoTheIndexViewInOrder() {
        final Book book = bookUpsertService.upsertFromSource(wordsOfRadiance(List.of(STORMLIGHT, COSMERE)));

        final List<BookIndexView> views = indexViews.indexViewsByIds(List.of(book.getBookId()));

        assertThat(views).singleElement().satisfies(view -> {
            assertThat(view.series()).containsExactly(STORMLIGHT, COSMERE);
            assertThat(view.authors()).containsExactly(DARROW, MUSTANG);
        });
    }

    private OffsetDateTime updatedAt(final Book book) {
        return jdbc.queryForObject(UPDATED_AT_SQL, OffsetDateTime.class, book.getBookId());
    }
}
