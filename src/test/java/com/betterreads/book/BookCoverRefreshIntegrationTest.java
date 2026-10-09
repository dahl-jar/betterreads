package com.betterreads.book;

import static com.betterreads.book.BookSeriesSamples.HARDCOVER_ID;
import static org.assertj.core.api.Assertions.assertThat;

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
class BookCoverRefreshIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String HARDCOVER_COVER = "https://hardcover.example.test/1.jpg";

    private static final String NEW_HARDCOVER_COVER = "https://hardcover.example.test/2.jpg";

    private static final String OPEN_LIBRARY_COVER = "https://openlibrary.example.test/1-L.jpg";

    private static final String SET_COVER_SQL =
        "UPDATE book SET cover_url = ?, cover_source = ?, cover_searched_at = now() WHERE book_id = ?";

    private static final String SOURCE_SQL = "UPDATE book SET cover_source = ? WHERE book_id = ?";

    private static final String COVER_SQL =
        "SELECT cover_url || ' ' || coalesce(cover_source, 'none') FROM book WHERE book_id = ?";

    @Autowired
    private BookUpsertService bookUpsertService;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clearCatalog() {
        bookRepository.deleteAll();
    }

    @Test
    void shouldKeepASearchedCoverOnRefresh() {
        final SourceBook source = covered(HARDCOVER_COVER);
        final Book book = bookUpsertService.upsertFromSource(source);
        jdbc.update(SET_COVER_SQL, OPEN_LIBRARY_COVER, "OPEN_LIBRARY", book.getBookId());

        bookUpsertService.upsertFromSource(source);

        assertThat(cover(book)).isEqualTo(OPEN_LIBRARY_COVER + " OPEN_LIBRARY");
    }

    @Test
    void shouldTakeTheMergedCoverForAnUnsearchedBook() {
        final Book book = bookUpsertService.upsertFromSource(covered(HARDCOVER_COVER));
        jdbc.update(SOURCE_SQL, "HARDCOVER", book.getBookId());

        bookUpsertService.upsertFromSource(covered(NEW_HARDCOVER_COVER));

        assertThat(cover(book)).isEqualTo(NEW_HARDCOVER_COVER + " none");
    }

    private String cover(final Book book) {
        return jdbc.queryForObject(COVER_SQL, String.class, book.getBookId());
    }

    private static SourceBook covered(final String coverUrl) {
        return SourceBook.builder(BookFieldSource.HARDCOVER)
            .hardcoverId(HARDCOVER_ID)
            .title("The Way of Kings")
            .coverUrl(coverUrl)
            .build();
    }
}
