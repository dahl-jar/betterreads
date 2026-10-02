package com.betterreads.book;

import static com.betterreads.book.BookSeriesSamples.COSMERE;
import static com.betterreads.book.BookSeriesSamples.STORMLIGHT;
import static com.betterreads.book.BookSeriesSamples.wordsOfRadiance;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

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
class BookSeriesChangeIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String CHANGES_SQL =
        "SELECT old_series || ' -> ' || new_series FROM book_series_change WHERE book_id = ?";

    private static final String QUEUED_SQL =
        "SELECT metadata_check_requested_at IS NOT NULL FROM book WHERE book_id = ?";

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
    void shouldRecordTheOldAndNewSeriesOfAnExistingBook() {
        final Book book = bookUpsertService.upsertFromSource(wordsOfRadiance(List.of(COSMERE)));

        bookUpsertService.upsertFromSource(wordsOfRadiance(List.of(STORMLIGHT, COSMERE)));

        assertThat(changesOf(book)).containsExactly("The Cosmere #12 -> The Stormlight Archive #2, The Cosmere #12");
    }

    @Test
    void shouldRecordNothingWhenTheSeriesIsUnchanged() {
        final Book book = bookUpsertService.upsertFromSource(wordsOfRadiance(List.of(COSMERE)));

        bookUpsertService.upsertFromSource(wordsOfRadiance(List.of(COSMERE)));

        assertThat(changesOf(book)).isEmpty();
    }

    @Test
    void shouldRecordNothingForANewBook() {
        final Book book = bookUpsertService.upsertFromSource(wordsOfRadiance(List.of(COSMERE)));

        final List<String> changes = changesOf(book);

        assertThat(changes).isEmpty();
    }

    @Test
    void shouldQueueTheChangedBookForTheCheck() {
        final Book book = bookUpsertService.upsertFromSource(wordsOfRadiance(List.of(COSMERE)));
        bookUpsertService.applyVerified(book.getBookId(), VerifiedMetadata.NONE);

        bookUpsertService.upsertFromSource(wordsOfRadiance(List.of(STORMLIGHT, COSMERE)));

        final Boolean queued = jdbc.queryForObject(QUEUED_SQL, Boolean.class, book.getBookId());
        assertThat(queued).isTrue();
    }

    @Test
    void shouldKeepAnEarlierCheckRequest() {
        final Book book = bookUpsertService.upsertFromSource(wordsOfRadiance(List.of(COSMERE)));
        jdbc.update("UPDATE book SET metadata_check_requested_at = now() - interval '7 days' WHERE book_id = ?",
            book.getBookId());

        bookUpsertService.upsertFromSource(wordsOfRadiance(List.of(STORMLIGHT, COSMERE)));

        final Boolean kept = jdbc.queryForObject(
            "SELECT metadata_check_requested_at < now() - interval '6 days' FROM book WHERE book_id = ?",
            Boolean.class, book.getBookId());
        assertThat(kept).isTrue();
    }

    private List<String> changesOf(final Book book) {
        return jdbc.queryForList(CHANGES_SQL, String.class, book.getBookId());
    }
}
