package com.betterreads.features.metadatacheck;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.betterreads.book.Author;
import com.betterreads.book.AuthorRepository;
import com.betterreads.book.Book;
import com.betterreads.book.BookUpsertService;
import com.betterreads.book.VerifiedMetadata;
import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.SeriesEntry;
import com.betterreads.booksource.SourceAuthor;
import com.betterreads.booksource.SourceBook;
import com.betterreads.clients.websearch.MetadataJson;
import com.betterreads.testsupport.ContainerizedTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
class MetadataCheckRepositoryTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final int PAGE_SIZE = 10;

    private static final int DAYS_IN_A_WEEK = 7;

    private static final String RED_RISING_KEY = "red-rising";

    private static final String GOLDEN_SON_KEY = "golden-son";

    private static final String AUTHOR = MetadataJson.AUTHOR;

    private static final String SAGA = MetadataJson.SERIES;

    private static final String UNIVERSE = MetadataJson.UNIVERSE;

    private static final String REQUESTED_SQL =
        "UPDATE book SET metadata_check_requested_at = now() - make_interval(days => ?) WHERE book_id = ?";

    @Autowired
    private MetadataCheckRepository books;

    @Autowired
    private BookUpsertService upsert;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AuthorRepository authors;

    @BeforeEach
    void clearBooks() {
        books.deleteAll();
        authors.deleteAll();
    }

    @Test
    void shouldReturnTheLongestWaitingBooksFirst() {
        final long addedToday = save("light-bringer");
        final long queuedLastWeek = save(RED_RISING_KEY);
        final long addedYesterday = save(GOLDEN_SON_KEY);
        jdbc.update(REQUESTED_SQL, DAYS_IN_A_WEEK, queuedLastWeek);
        jdbc.update("UPDATE book SET created_at = now() - interval '30 days' WHERE book_id = ?", queuedLastWeek);
        jdbc.update(REQUESTED_SQL, 1, addedYesterday);

        final List<Book> found = books.findDueForCheck(PageRequest.ofSize(PAGE_SIZE));

        assertThat(found).extracting(Book::getBookId).containsExactly(queuedLastWeek, addedYesterday, addedToday);
    }

    @Test
    void shouldLeaveOutACheckedBook() {
        final long waiting = save(RED_RISING_KEY);
        final long checked = save(GOLDEN_SON_KEY);
        upsert.applyVerified(checked, VerifiedMetadata.NONE);

        final List<Book> found = books.findDueForCheck(PageRequest.ofSize(PAGE_SIZE));

        assertThat(found).extracting(Book::getBookId).containsExactly(waiting);
    }

    @Test
    void shouldLoadAuthors() {
        upsert.upsertFromSource(redRising().build());

        final List<Book> found = books.findDueForCheck(PageRequest.ofSize(PAGE_SIZE));

        assertThat(found.getFirst().getAuthors()).extracting(Author::getName).containsExactly(AUTHOR);
    }

    @Test
    void shouldLoadSeries() {
        final List<SeriesEntry> series = List.of(new SeriesEntry(SAGA, 1), new SeriesEntry(UNIVERSE, 1));
        upsert.upsertFromSource(redRising().series(series).build());

        final List<Book> found = books.findDueForCheck(PageRequest.ofSize(PAGE_SIZE));

        assertThat(found.getFirst().getSeries()).isEqualTo(series);
    }

    @Test
    void shouldListTheNamesOfEverySeries() {
        final SourceBook book =
            redRising().series(List.of(new SeriesEntry(SAGA, 1), new SeriesEntry(UNIVERSE, 1))).build();
        upsert.upsertFromSource(book);

        final List<String> names = books.findSeriesNames();

        assertThat(names).containsExactlyInAnyOrder(SAGA, UNIVERSE);
    }

    private static SourceBook.Builder redRising() {
        return SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
            .openLibraryWorkKey("OL1W")
            .title("Red Rising")
            .authors(List.of(SourceAuthor.ofName(AUTHOR)));
    }

    private long save(final String dedupKey) {
        final Book book = new Book();
        book.setDedupKey(dedupKey);
        book.setTitle(dedupKey);
        return books.save(book).getBookId();
    }
}
