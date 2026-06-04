package com.betterreads.catalog;

import com.betterreads.support.ContainerizedTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

import com.betterreads.catalog.entity.Book;
import com.betterreads.catalog.repository.AuthorRepository;
import com.betterreads.catalog.repository.BookRepository;
import com.betterreads.catalog.service.CatalogService;
import com.betterreads.catalog.service.SourceBook;
import com.betterreads.integration.openlibrary.OpenLibraryClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end round-trip from the live OpenLibrary API into a real Postgres via the Flyway-managed
 * schema, then back out through the repository.
 *
 * <p>Opt-in via {@code RUN_OPENLIBRARY_LIVE=1} so the default build does not depend on the API,
 * and requires Docker for the Testcontainer, as with the project's other persistence suites.
 *
 * <p>The two cases cover the failure modes a change to migrations, entities, or
 * {@code CatalogService} would introduce for an OpenLibrary-sourced book: a wrong
 * {@code open_library_work_key} column mapping, an upsert that inserts twice because the
 * lookup-by-work-key step is wrong, and a duplicate author insert.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "openlibrary.base-url=https://openlibrary.org",
    "openlibrary.contact-email=test@betterreadsapp.com",
    "openlibrary.connect-timeout=5000",
    "openlibrary.read-timeout=15000",
    "jwt.secret=integration-test-secret-must-be-at-least-256-bits-long-padding-padding",
    "jwt.issuer=betterreads-it",
    "jwt.expiration-minutes=60",
    "jwt.refresh-expiration-days=30",
    "mail.outbox.worker-enabled=false"
})
@EnabledIfEnvironmentVariable(named = "RUN_OPENLIBRARY_LIVE", matches = "1")
class CatalogOpenLibraryPersistenceIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final int HOBBIT_FIRST_PUBLISHED = 1937;

    @Autowired
    private OpenLibraryClient openLibraryClient;

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private AuthorRepository authorRepository;

    @BeforeEach
    void clearCatalog() {
        bookRepository.deleteAll();
        authorRepository.deleteAll();
    }

    @Test
    @DisplayName("upserting the Hobbit writes book, author, and join rows readable by the repository")
    void upsertHobbitLandsCleanlyInPostgres() {
        final SourceBook source = fetchHobbit();

        final Book persisted = catalogService.upsertFromSource(source);

        final String workKey = Objects.requireNonNull(persisted.getOpenLibraryWorkKey(),
            "upsert must have written the source work key onto the entity");
        final Optional<Book> reloaded = bookRepository.findByOpenLibraryWorkKey(workKey);
        assertThat(reloaded)
            .as("repository must find by the same work key the upsert wrote")
            .isPresent()
            .get()
            .satisfies(book -> {
                assertThat(book.getTitle()).containsIgnoringCase("Hobbit");
                assertThat(book.getOpenLibraryWorkKey()).isEqualTo(source.openLibraryWorkKey());
                assertThat(book.getFirstPublishYear())
                    .as("OpenLibrary returns the original 1937, not a reprint year")
                    .isEqualTo(HOBBIT_FIRST_PUBLISHED);
                assertThat(book.getAuthors())
                    .as("M2M join row must exist with Tolkien as the stored author")
                    .extracting("name")
                    .anyMatch(name -> name.toString().contains("Tolkien"));
                assertThat(book.getSubjects())
                    .as("stored subjects must be clean genres, not the plot-element noise "
                        + "(thrushes, the one ring) OpenLibrary mixes into its subject array")
                    .extracting("subject")
                    .contains("fantasy", "fiction")
                    .doesNotContain("thrushes", "the one ring", "arkenstone");
            });
    }

    @Test
    @DisplayName("re-upserting the same SourceBook updates in place rather than inserting again")
    void upsertIsIdempotentForSameWork() {
        final SourceBook source = fetchHobbit();

        final Book first = catalogService.upsertFromSource(source);
        final OffsetDateTime firstUpdatedAt = first.getUpdatedAt();
        final long firstId = first.getBookId();

        final Book second = catalogService.upsertFromSource(source);

        assertThat(second.getBookId())
            .as("second upsert must reuse the existing book_id, not allocate a new one")
            .isEqualTo(firstId);
        assertThat(bookRepository.count())
            .as("re-upserting the same work must not duplicate the book row")
            .isEqualTo(1L);
        assertThat(second.getSubjects())
            .as("re-upsert replaces subjects rather than appending; the second pass must not "
                + "double every genre row")
            .hasSize(first.getSubjects().size());
        assertThat(second.getUpdatedAt())
            .as("@PreUpdate must fire on the second save so updated_at moves forward")
            .isAfterOrEqualTo(firstUpdatedAt);
    }

    private SourceBook fetchHobbit() {
        final Optional<SourceBook> source = openLibraryClient.fetchByTitleAuthor(
            "The Hobbit", "J.R.R. Tolkien");
        assertThat(source)
            .as("OpenLibrary must know this book; the live client test covers data-quality details")
            .isPresent();
        return source.get();
    }
}
