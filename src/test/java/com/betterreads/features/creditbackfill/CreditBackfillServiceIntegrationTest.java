package com.betterreads.features.creditbackfill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Optional;

import com.betterreads.book.AuthorRepository;
import com.betterreads.book.BookRepository;
import com.betterreads.book.BookUpsertService;
import com.betterreads.book.VerifiedMetadata;
import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.CreditRole;
import com.betterreads.booksource.SourceAuthor;
import com.betterreads.booksource.SourceBook;
import com.betterreads.clients.hardcoverbook.HardcoverClient;
import com.betterreads.testsupport.ContainerizedTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "betterreads.catalog.staging.poll-enabled=false",
    "betterreads.catalog.credit-backfill.pause=0s"
})
class CreditBackfillServiceIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String HARDCOVER_ID = "379217";

    private static final String OTHER_HARDCOVER_ID = "379218";

    private static final String ROTHFUSS = "Patrick Rothfuss";

    private static final String SIMONETTI = "Marc Simonetti";

    private static final String AS_AUTHOR = " AUTHOR";

    private static final String AS_ILLUSTRATOR = " ILLUSTRATOR";

    private static final String CREDITS_SQL = """
        SELECT author.name || ' ' || book_author.role
        FROM book_author
        INNER JOIN author ON author.author_id = book_author.author_id
        ORDER BY book_author.position
        """;

    private static final String CHECKED_SQL = "SELECT credits_checked_at IS NOT NULL FROM book WHERE book_id = ?";

    @Autowired
    private CreditBackfillService service;

    @Autowired
    private BookUpsertService upserts;

    @Autowired
    private BookRepository books;

    @Autowired
    private AuthorRepository authors;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private HardcoverClient hardcover;

    @BeforeEach
    void setUp() {
        books.deleteAll();
        authors.deleteAll();
    }

    @Test
    void shouldApplyRolesFromHardcover() {
        final long bookId = seed();
        when(hardcover.fetchByHardcoverId(HARDCOVER_ID)).thenReturn(Optional.of(nameOfTheWind(List.of(
            SourceAuthor.ofName(ROTHFUSS), SourceAuthor.withRole(SIMONETTI, CreditRole.ILLUSTRATOR)))));

        service.backfillSlice();

        assertThat(jdbc.queryForList(CREDITS_SQL, String.class))
            .containsExactly(ROTHFUSS + AS_AUTHOR, SIMONETTI + AS_ILLUSTRATOR);
        assertThat(jdbc.queryForObject(CHECKED_SQL, Boolean.class, bookId)).isTrue();
    }

    @Test
    void shouldDeleteUncreditedAuthors() {
        seed();
        when(hardcover.fetchByHardcoverId(HARDCOVER_ID)).thenReturn(Optional.of(nameOfTheWind(List.of(
            SourceAuthor.ofName(ROTHFUSS)))));

        service.backfillSlice();

        assertThat(jdbc.queryForList("SELECT name FROM author", String.class)).containsExactly(ROTHFUSS);
    }

    @Test
    void shouldKeepVerifiedAuthorNames() {
        final long bookId = seed();
        upserts.applyVerified(bookId, new VerifiedMetadata(
            null, List.of(SIMONETTI, ROTHFUSS), null, null, null, null, null, null));
        when(hardcover.fetchByHardcoverId(HARDCOVER_ID)).thenReturn(Optional.of(nameOfTheWind(List.of(
            SourceAuthor.ofName(ROTHFUSS),
            SourceAuthor.withRole(SIMONETTI, CreditRole.ILLUSTRATOR),
            SourceAuthor.withRole("Translator", CreditRole.TRANSLATOR)))));

        service.backfillSlice();

        assertThat(jdbc.queryForList(CREDITS_SQL, String.class))
            .containsExactly(ROTHFUSS + AS_AUTHOR, SIMONETTI + AS_ILLUSTRATOR);
    }

    @Nested
    class Failures {

        @Test
        void shouldLeaveBookUncheckedOnError() {
            final long bookId = seed();
            when(hardcover.fetchByHardcoverId(HARDCOVER_ID))
                .thenThrow(WebClientResponseException.create(
                    HttpStatus.BAD_GATEWAY.value(), "Bad Gateway", null, null, null));

            service.backfillSlice();

            assertThat(jdbc.queryForObject(CHECKED_SQL, Boolean.class, bookId)).isFalse();
        }

        @Test
        void shouldLeaveBookUncheckedOnTimeout() {
            final long bookId = seed();
            when(hardcover.fetchByHardcoverId(HARDCOVER_ID)).thenThrow(new WebClientRequestException(
                new IOException("timed out"), HttpMethod.POST, URI.create("http://hardcover.test"), HttpHeaders.EMPTY));

            service.backfillSlice();

            assertThat(jdbc.queryForObject(CHECKED_SQL, Boolean.class, bookId)).isFalse();
        }

        @Test
        void shouldLeaveBookUncheckedWhenRateLimited() {
            final long bookId = seed();
            when(hardcover.fetchByHardcoverId(HARDCOVER_ID)).thenThrow(rateLimited());

            service.backfillSlice();

            assertThat(jdbc.queryForObject(CHECKED_SQL, Boolean.class, bookId)).isFalse();
        }

        @Test
        void shouldStopSliceWhenRateLimited() {
            seed();
            upserts.upsertFromSource(SourceBook.builder(BookFieldSource.HARDCOVER)
                .hardcoverId(OTHER_HARDCOVER_ID)
                .title("The Wise Man's Fear")
                .authors(SourceAuthor.ofNames(List.of(ROTHFUSS)))
                .build());
            when(hardcover.fetchByHardcoverId(anyString())).thenThrow(rateLimited());

            service.backfillSlice();

            verify(hardcover, times(1)).fetchByHardcoverId(anyString());
        }

        @Test
        void shouldMarkCheckedOnNotFound() {
            final long bookId = seed();
            when(hardcover.fetchByHardcoverId(HARDCOVER_ID))
                .thenThrow(WebClientResponseException.create(
                    HttpStatus.NOT_FOUND.value(), "Not Found", null, null, null));

            service.backfillSlice();

            assertThat(jdbc.queryForObject(CHECKED_SQL, Boolean.class, bookId)).isTrue();
        }

        @Test
        void shouldMarkCheckedWhenHardcoverHasNoBook() {
            final long bookId = seed();
            when(hardcover.fetchByHardcoverId(HARDCOVER_ID)).thenReturn(Optional.empty());

            service.backfillSlice();

            assertThat(jdbc.queryForObject(CHECKED_SQL, Boolean.class, bookId)).isTrue();
            assertThat(jdbc.queryForList(CREDITS_SQL, String.class))
                .containsExactly(ROTHFUSS + AS_AUTHOR, SIMONETTI + AS_AUTHOR);
        }
    }

    private static WebClientResponseException rateLimited() {
        return WebClientResponseException.create(
            HttpStatus.TOO_MANY_REQUESTS.value(), "Too Many Requests", null, null, null);
    }

    private long seed() {
        return upserts.upsertFromSource(nameOfTheWind(SourceAuthor.ofNames(List.of(ROTHFUSS, SIMONETTI))))
            .getBookId();
    }

    private static SourceBook nameOfTheWind(final List<SourceAuthor> credits) {
        return SourceBook.builder(BookFieldSource.HARDCOVER)
            .hardcoverId(HARDCOVER_ID)
            .title("The Name of the Wind")
            .authors(credits)
            .build();
    }
}
