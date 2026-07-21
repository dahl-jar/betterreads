package com.betterreads.catalog.source;

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
import com.betterreads.catalog.service.write.BookUpsertService;
import com.betterreads.catalog.service.source.model.SourceBook;
import com.betterreads.integration.openlibrary.OpenLibraryClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Persists live OpenLibrary data through the Flyway-managed Postgres schema. */
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
    private BookUpsertService bookUpsertService;

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
    @DisplayName("upserting the Hobbit persists its book, author, and join rows")
    void upsertHobbitPersistsRelationships() {
        final SourceBook source = fetchHobbit();

        final Book persisted = bookUpsertService.upsertFromSource(source);

        final String workKey = Objects.requireNonNull(persisted.getOpenLibraryWorkKey(),
            "upsert must have written the source work key onto the entity");
        final Optional<Book> reloaded = bookRepository.findByOpenLibraryWorkKey(workKey);
        assertThat(reloaded)
            .as("repository finds the persisted work key")
            .isPresent()
            .get()
            .satisfies(book -> {
                assertThat(book.getTitle()).containsIgnoringCase("Hobbit");
                assertThat(book.getOpenLibraryWorkKey()).isEqualTo(source.openLibraryWorkKey());
                assertThat(book.getFirstPublishYear())
                    .as("OpenLibrary returns the original 1937 publication year")
                    .isEqualTo(HOBBIT_FIRST_PUBLISHED);
                assertThat(book.getAuthors())
                    .as("stored book has a Tolkien author row")
                    .extracting("name")
                    .anyMatch(name -> name.toString().contains("Tolkien"));
                assertThat(book.getSubjects())
                    .as("stored subjects contain catalog genres and omit plot elements")
                    .extracting("subject")
                    .contains("fantasy", "fiction")
                    .doesNotContain("thrushes", "the one ring", "arkenstone");
            });
    }

    @Test
    @DisplayName("re-upserting the same SourceBook reuses the existing rows")
    void upsertIsIdempotentForSameWork() {
        final SourceBook source = fetchHobbit();

        final Book first = bookUpsertService.upsertFromSource(source);
        final OffsetDateTime firstUpdatedAt = first.getUpdatedAt();
        final long firstId = first.getBookId();

        final Book second = bookUpsertService.upsertFromSource(source);

        assertThat(second.getBookId())
            .as("second upsert reuses the existing book_id")
            .isEqualTo(firstId);
        assertThat(bookRepository.count())
            .as("book row count remains one after re-upsert")
            .isEqualTo(1L);
        assertThat(second.getSubjects())
            .as("subject row count remains stable after re-upsert")
            .hasSize(first.getSubjects().size());
        assertThat(second.getUpdatedAt())
            .as("updated_at advances on the second save")
            .isAfterOrEqualTo(firstUpdatedAt);
    }

    private SourceBook fetchHobbit() {
        final Optional<SourceBook> source = openLibraryClient.fetchByTitleAuthor(
            "The Hobbit", "J.R.R. Tolkien");
        assertThat(source)
            .as("OpenLibrary returns the Hobbit")
            .isPresent();
        return source.get();
    }
}
