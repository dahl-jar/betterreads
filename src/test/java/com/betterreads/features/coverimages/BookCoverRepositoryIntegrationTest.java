package com.betterreads.features.coverimages;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.betterreads.book.Book;
import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.SourceBook;
import com.betterreads.testsupport.ContainerizedTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
class BookCoverRepositoryIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String ISBN = "9780345539786";

    private static final String GOLDEN_SON_ISBN = "9780345539816";

    private static final String OLD_COVER = "https://covers.example.test/1.jpg";

    private static final String NEW_COVER = "https://covers.example.test/2.jpg";

    private static final String OBJECT_KEY = "covers/1.webp";

    private static final int PAGE_SIZE = 10;

    @Autowired
    private BookCoverRepository covers;

    @AfterEach
    void tearDown() {
        covers.deleteAll();
    }

    @Test
    void shouldQueueMirroredCoverAgainWhenUrlChanges() {
        final Book book = new Book();
        book.applyFrom(redRising(OLD_COVER));
        final long bookId = covers.save(book).getBookId();
        covers.markCoverMirrored(bookId, OBJECT_KEY, OffsetDateTime.now(ZoneOffset.UTC));
        final Book mirrored = covers.findById(bookId).orElseThrow();
        mirrored.applyFrom(redRising(NEW_COVER));
        covers.save(mirrored);

        final List<Book> candidates =
            covers.findCoverSweepCandidates(OffsetDateTime.now(ZoneOffset.UTC), PageRequest.of(0, PAGE_SIZE));

        assertThat(candidates).extracting(Book::getBookId).containsExactly(bookId);
    }

    @Test
    void shouldSkipCoverCheckedSinceRunStart() {
        final long checkedBefore = save(redRising(OLD_COVER));
        final long checkedSince = save(goldenSon());
        final OffsetDateTime runStart = OffsetDateTime.now(ZoneOffset.UTC);
        covers.markCoverChecked(checkedBefore, runStart.minusSeconds(1));
        covers.markCoverChecked(checkedSince, runStart.plusSeconds(1));

        final List<Book> candidates = covers.findCoverSweepCandidates(runStart, PageRequest.of(0, PAGE_SIZE));

        assertThat(candidates).extracting(Book::getBookId).containsExactly(checkedBefore);
    }

    @Test
    void shouldPutCheckedCoverBehindUncheckedOne() {
        final long checked = save(redRising(OLD_COVER));
        final long unchecked = save(goldenSon());
        final OffsetDateTime checkedAt = OffsetDateTime.now(ZoneOffset.UTC);
        covers.markCoverChecked(checked, checkedAt);

        final List<Book> candidates =
            covers.findCoverSweepCandidates(checkedAt.plusSeconds(1), PageRequest.of(0, PAGE_SIZE));

        assertThat(candidates).extracting(Book::getBookId).containsExactly(unchecked, checked);
    }

    private long save(final SourceBook source) {
        final Book book = new Book();
        book.applyFrom(source);
        return covers.save(book).getBookId();
    }

    private static SourceBook redRising(final String coverUrl) {
        return source(ISBN, "Red Rising", coverUrl);
    }

    private static SourceBook goldenSon() {
        return source(GOLDEN_SON_ISBN, "Golden Son", OLD_COVER);
    }

    private static SourceBook source(final String isbn, final String title, final String coverUrl) {
        return SourceBook.builder(BookFieldSource.HARDCOVER)
            .title(title)
            .isbn13(isbn)
            .coverUrl(coverUrl)
            .build();
    }
}
