package com.betterreads.features.metadatacheck;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.betterreads.book.Author;
import com.betterreads.book.AuthorRepository;
import com.betterreads.book.Book;
import com.betterreads.book.BookUpsertService;
import com.betterreads.book.VerifiedMetadata;
import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.SourceAuthor;
import com.betterreads.booksource.SourceBook;
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

    private static final int LOOKBACK_DAYS = 7;

    private static final String AUTHOR = "Pierce Brown";

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
    void shouldFindNewUncheckedBooks() {
        final OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        final long unchecked = save("red-rising");
        final long checked = save("golden-son");
        upsert.applyVerified(checked, VerifiedMetadata.NONE);
        final long old = save("morning-star");
        jdbc.update("UPDATE book SET created_at = now() - interval '30 days' WHERE book_id = ?", old);

        final List<Book> found =
            books.findUncheckedSince(now.minusDays(LOOKBACK_DAYS), PageRequest.ofSize(PAGE_SIZE));

        assertThat(found).extracting(Book::getBookId).containsExactly(unchecked);
    }

    @Test
    void shouldLoadAuthors() {
        final OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        upsert.upsertFromSource(SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
            .openLibraryWorkKey("OL1W")
            .title("Red Rising")
            .authors(List.of(SourceAuthor.ofName(AUTHOR)))
            .build());

        final List<Book> found =
            books.findUncheckedSince(now.minusDays(LOOKBACK_DAYS), PageRequest.ofSize(PAGE_SIZE));

        assertThat(found.getFirst().getAuthors()).extracting(Author::getName).containsExactly(AUTHOR);
    }

    private long save(final String dedupKey) {
        final Book book = new Book();
        book.setDedupKey(dedupKey);
        book.setTitle(dedupKey);
        return books.save(book).getBookId();
    }
}
