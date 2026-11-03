package com.betterreads.features.metadatacheck;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;

import com.betterreads.book.AuthorRepository;
import com.betterreads.book.Book;
import com.betterreads.book.BookUpsertService;
import com.betterreads.book.VerifiedMetadata;
import com.betterreads.booksource.SeriesEntry;
import com.betterreads.clients.websearch.SeriesBook;
import com.betterreads.booksource.SourceBook;
import com.betterreads.clients.websearch.MetadataJson;
import com.betterreads.testsupport.Books;
import com.betterreads.testsupport.ContainerizedTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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

    private static final String LIGHT_BRINGER_KEY = "light-bringer";

    private static final String SAGA = MetadataJson.SERIES;

    private static final String UNIVERSE = MetadataJson.UNIVERSE;

    private static final int SERIES_LENGTH = 32;

    private static final int LISTED_BOOKS = 30;

    private static final String BOOK_KEY_PREFIX = "book-";

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

    private List<Long> due() {
        return due(PAGE_SIZE);
    }

    private List<Long> due(final int pageSize) {
        return books.findIdsDueForCheck(OffsetDateTime.now(ZoneOffset.UTC), PageRequest.ofSize(pageSize));
    }

    @Nested
    class Queue {

        @Test
        void shouldReturnTheLongestWaitingBooksFirst() {
            final long addedToday = save(LIGHT_BRINGER_KEY);
            final long queuedLastWeek = save(RED_RISING_KEY);
            final long addedYesterday = save(GOLDEN_SON_KEY);
            jdbc.update(REQUESTED_SQL, DAYS_IN_A_WEEK, queuedLastWeek);
            jdbc.update("UPDATE book SET created_at = now() - interval '30 days' WHERE book_id = ?", queuedLastWeek);
            jdbc.update(REQUESTED_SQL, 1, addedYesterday);

            final List<Long> found = due();

            assertThat(found).containsExactly(queuedLastWeek, addedYesterday, addedToday);
        }

        @Test
        void shouldLeaveOutACheckedBook() {
            final long waiting = save(RED_RISING_KEY);
            final long checked = save(GOLDEN_SON_KEY);
            upsert.applyVerified(checked, VerifiedMetadata.NONE, 1);

            final List<Long> found = due();

            assertThat(found).containsExactly(waiting);
        }

        @Test
        void shouldLeaveOutABookNotYetDue() {
            final long due = save(RED_RISING_KEY);
            final long retryTomorrow = save(GOLDEN_SON_KEY);
            jdbc.update(REQUESTED_SQL, -1, retryTomorrow);

            final List<Long> found = due();

            assertThat(found).containsExactly(due);
        }

        @Test
        void shouldPutLowerIdFirstWhenRequestedAtSameTime() {
            final long first = save(RED_RISING_KEY);
            final long second = save(GOLDEN_SON_KEY);
            save(LIGHT_BRINGER_KEY);
            jdbc.update("UPDATE book SET metadata_check_requested_at = now() - interval '1 day'");

            final List<Long> found = due(2);

            assertThat(found).containsExactly(first, second);
        }
    }

    @Nested
    class Series {

        @Test
        void shouldListTheNamesOfEverySeries() {
            final List<SeriesEntry> series = List.of(new SeriesEntry(SAGA, 1), new SeriesEntry(UNIVERSE, 1));
            final SourceBook.Builder book = MetadataCheckSamples.source();
            book.series(series);
            upsert.upsertFromSource(book.build());

            final List<String> names = books.findSeriesNames();

            assertThat(names).containsExactlyInAnyOrder(SAGA, UNIVERSE);
        }

        @Test
        void shouldListTheOtherBooksOfASeriesByPosition() {
            final List<Long> fromLastToFirst = IntStream.iterate(SERIES_LENGTH, position -> position - 1)
                .limit(SERIES_LENGTH)
                .mapToObj(MetadataCheckRepositoryTest.this::saveAtPosition)
                .toList();
            final long first = fromLastToFirst.getLast();
            final List<SeriesBook> expected = IntStream.rangeClosed(2, LISTED_BOOKS + 1)
                .mapToObj(position -> new SeriesBook(BOOK_KEY_PREFIX + position, position))
                .toList();

            final List<SeriesBook> found = books.findSeriesBooks(SAGA, first);

            assertThat(found).containsExactlyElementsOf(expected);
        }

        @Test
        void shouldLeaveOutBooksOfAnotherSeries() {
            final long first = saveAtPosition(1);
            final long other = save(BOOK_KEY_PREFIX + "other");
            jdbc.update("INSERT INTO book_series (book_id, ordinal, series_name, position) VALUES (?, 0, ?, 2)",
                other, UNIVERSE);

            final List<SeriesBook> found = books.findSeriesBooks(SAGA, first);

            assertThat(found).isEmpty();
        }
    }

    private long saveAtPosition(final int position) {
        final long bookId = save(BOOK_KEY_PREFIX + position);
        jdbc.update("INSERT INTO book_series (book_id, ordinal, series_name, position) VALUES (?, 0, ?, ?)",
            bookId, SAGA, position);
        return bookId;
    }

    private long save(final String dedupKey) {
        final Book saved = books.save(Books.book(dedupKey, dedupKey));
        return saved.getBookId();
    }
}
