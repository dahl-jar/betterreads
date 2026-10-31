package com.betterreads.features.descriptionbackfill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.betterreads.book.Author;
import com.betterreads.book.AuthorRepository;
import com.betterreads.book.Book;
import com.betterreads.book.VerifiedMetadata;
import com.betterreads.bookdescription.DescriptionSelector;
import com.betterreads.booksource.DescriptionLookup;
import com.betterreads.testsupport.Books;
import com.betterreads.testsupport.ContainerizedTest;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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

    private static final String MORNING_STAR_KEY = "morning-star";

    private static final String MORNING_STAR_ISBN = "9780345539847";

    private static final String AUTHOR = "author";

    private static final String SAME_TIME = "2026-01-01T00:00:00Z";

    private static final int THIN_LENGTH = 200;

    private static final int PAGE_SIZE = 10;

    private static final String THIN = "A short stub.";

    private static final String FULL = "x".repeat(THIN_LENGTH);

    private static final String STRONG = "A full description of the Institute and the war that follows it.";

    private static final String COMMUNITY_AVERAGE = "4.50";

    private static final int COMMUNITY_COUNT = 2;

    @MockitoBean
    private DescriptionSelector selector;

    @Autowired
    private BookDescriptionRepository books;

    @Autowired
    private AuthorRepository authors;

    @Autowired
    private DescriptionBackfillService service;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clearBooks() {
        books.deleteAll();
        authors.deleteAll();
    }

    @Test
    void shouldReturnThinKeyedBooksWithTheNeverCheckedFirst() {
        final long checked = save(RED_RISING_KEY, RED_RISING_ISBN, THIN);
        books.markDescriptionChecked(checked, OffsetDateTime.now(ZoneOffset.UTC));
        final long neverChecked = save(GOLDEN_SON_KEY, GOLDEN_SON_ISBN, THIN);
        save(MORNING_STAR_KEY, MORNING_STAR_ISBN, FULL);
        save("empire-of-silence", null, THIN);

        final List<Long> candidates = books.findIdsWithThinDescription(THIN_LENGTH, PageRequest.ofSize(PAGE_SIZE));

        assertThat(candidates).containsExactly(neverChecked, checked);
    }

    @Test
    void shouldSkipVerifiedDescription() {
        saveVerified();

        final List<Long> candidates = books.findIdsWithThinDescription(THIN_LENGTH, PageRequest.ofSize(PAGE_SIZE));

        assertThat(candidates).isEmpty();
    }

    @Test
    void shouldPutLowerIdFirstWhenThinDescriptionsTie() {
        final long first = save(RED_RISING_KEY, RED_RISING_ISBN, THIN);
        final long second = save(GOLDEN_SON_KEY, GOLDEN_SON_ISBN, THIN);
        final long third = save(MORNING_STAR_KEY, MORNING_STAR_ISBN, THIN);
        setUpdatedAt(third);
        setUpdatedAt(second);
        setUpdatedAt(first);

        final List<Long> candidates = books.findIdsWithThinDescription(THIN_LENGTH, PageRequest.ofSize(2));

        assertThat(candidates).containsExactly(first, second);
    }

    @Test
    void shouldSendFirstAuthorToDescriptionSources() {
        final Author author = authors.save(Books.author(AUTHOR));
        final Book book = Books.promoted(RED_RISING_KEY, RED_RISING_KEY, author);
        book.setIsbn(RED_RISING_ISBN);
        book.setDescription(THIN);
        books.save(book);
        final ArgumentCaptor<DescriptionLookup> lookup = ArgumentCaptor.captor();

        service.backfillSlice();

        verify(selector).bestDescription(lookup.capture(), any());
        final DescriptionLookup sent = lookup.getValue();
        assertThat(sent.author()).isEqualTo(AUTHOR);
    }

    @Test
    void shouldSweepPastVerifiedDescription() {
        saveVerified();
        final long open = save(GOLDEN_SON_KEY, GOLDEN_SON_ISBN, THIN);

        final List<Long> swept = books.findIdsKeyed(PageRequest.ofSize(PAGE_SIZE));

        assertThat(swept).containsExactly(open);
    }

    @Test
    void shouldWalkKeyedBooksAcrossPages() {
        final long first = save(RED_RISING_KEY, RED_RISING_ISBN, THIN);
        final long second = save(GOLDEN_SON_KEY, GOLDEN_SON_ISBN, THIN);
        final long third = save(MORNING_STAR_KEY, MORNING_STAR_ISBN, THIN);

        final List<Long> firstPage = books.findIdsKeyed(PageRequest.of(0, 2));
        final List<Long> secondPage = books.findIdsKeyed(PageRequest.of(1, 2));

        assertThat(firstPage).containsExactly(first, second);
        assertThat(secondPage).containsExactly(third);
    }

    @Test
    void shouldNotOverwriteVerifiedDescription() {
        final long bookId = saveVerified();

        books.updateDescription(bookId, STRONG, OffsetDateTime.now(ZoneOffset.UTC));

        assertThat(books.findById(bookId).orElseThrow().getDescription()).isEqualTo(THIN);
    }

    private long saveVerified() {
        final Book book = book(RED_RISING_KEY, RED_RISING_ISBN, THIN);
        final VerifiedMetadata verified = new VerifiedMetadata(null, null, null, null, null, THIN, null, null);
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

    private void setUpdatedAt(final long bookId) {
        jdbc.update("UPDATE book SET updated_at = ?::timestamptz WHERE book_id = ?", SAME_TIME, bookId);
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
