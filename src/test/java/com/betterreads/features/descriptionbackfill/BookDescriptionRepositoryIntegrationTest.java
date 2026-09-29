package com.betterreads.features.descriptionbackfill;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.betterreads.book.Book;
import com.betterreads.book.VerifiedMetadata;
import com.betterreads.testsupport.ContainerizedTest;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
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
class BookDescriptionRepositoryIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String RED_RISING_KEY = "red-rising";

    private static final String RED_RISING_ISBN = "9780345539786";

    private static final String GOLDEN_SON_KEY = "golden-son";

    private static final String GOLDEN_SON_ISBN = "9780345539816";

    private static final int THIN_LENGTH = 200;

    private static final int PAGE_SIZE = 10;

    private static final String THIN = "A short stub.";

    private static final String FULL = "x".repeat(THIN_LENGTH);

    private static final String STRONG = "A full description of the Institute and the war that follows it.";

    private static final String COMMUNITY_AVERAGE = "4.50";

    private static final int COMMUNITY_COUNT = 2;

    @Autowired
    private BookDescriptionRepository books;

    @BeforeEach
    void clearBooks() {
        books.deleteAll();
    }

    @Test
    void shouldReturnThinKeyedBooksWithTheNeverCheckedFirst() {
        final long checked = save(RED_RISING_KEY, RED_RISING_ISBN, THIN);
        books.markDescriptionChecked(checked, OffsetDateTime.now(ZoneOffset.UTC));
        final long neverChecked = save(GOLDEN_SON_KEY, GOLDEN_SON_ISBN, THIN);
        save("morning-star", "9780345539847", FULL);
        save("empire-of-silence", null, THIN);

        final List<Book> candidates = books.findThinDescriptions(THIN_LENGTH, PageRequest.ofSize(PAGE_SIZE));

        assertThat(candidates).extracting(Book::getBookId).containsExactly(neverChecked, checked);
    }

    @Test
    void shouldSkipVerifiedDescription() {
        saveVerified();

        final List<Book> candidates = books.findThinDescriptions(THIN_LENGTH, PageRequest.ofSize(PAGE_SIZE));

        assertThat(candidates).isEmpty();
    }

    @Test
    void shouldSweepPastVerifiedDescription() {
        saveVerified();
        final long open = save(GOLDEN_SON_KEY, GOLDEN_SON_ISBN, THIN);

        final List<Book> swept = books.findAllKeyedBooks(PageRequest.ofSize(PAGE_SIZE));

        assertThat(swept).extracting(Book::getBookId).containsExactly(open);
    }

    @Test
    void shouldNotOverwriteVerifiedDescription() {
        final long bookId = saveVerified();

        books.updateDescription(bookId, STRONG, OffsetDateTime.now(ZoneOffset.UTC));

        assertThat(books.findById(bookId).orElseThrow().getDescription()).isEqualTo(THIN);
    }

    private long saveVerified() {
        final Book book = book(RED_RISING_KEY, RED_RISING_ISBN, THIN);
        final VerifiedMetadata verified = new VerifiedMetadata(null, null, null, null, null, THIN, null);
        book.applyVerified(verified, OffsetDateTime.now(ZoneOffset.UTC));
        return books.save(book).getBookId();
    }

    @Test
    void shouldKeepTheCommunityRatingWhenWritingADescription() {
        final Book book = book(RED_RISING_KEY, RED_RISING_ISBN, THIN);
        book.applyCommunityAggregate(new BigDecimal(COMMUNITY_AVERAGE), COMMUNITY_COUNT);
        final long bookId = books.save(book).getBookId();

        books.updateDescription(bookId, STRONG, OffsetDateTime.now(ZoneOffset.UTC));

        final Book updated = books.findById(bookId).orElseThrow();
        assertThat(updated.getDescription()).isEqualTo(STRONG);
        assertThat(updated.getCommunityAverage()).isEqualByComparingTo(COMMUNITY_AVERAGE);
        assertThat(updated.getCommunityCount()).isEqualTo(COMMUNITY_COUNT);
    }

    private long save(final String dedupKey, final @Nullable String isbn, final String description) {
        return books.save(book(dedupKey, isbn, description)).getBookId();
    }

    private static Book book(final String dedupKey, final @Nullable String isbn, final String description) {
        final Book book = new Book();
        book.setDedupKey(dedupKey);
        book.setTitle(dedupKey);
        book.setIsbn(isbn);
        book.setDescription(description);
        return book;
    }
}
