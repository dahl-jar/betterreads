package com.betterreads.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

import com.betterreads.catalog.entity.Book;
import com.betterreads.catalog.repository.AuthorRepository;
import com.betterreads.catalog.repository.BookRepository;
import com.betterreads.catalog.service.CatalogService;
import com.betterreads.catalog.service.SourceBook;
import com.betterreads.integration.googlebooks.GoogleBooksClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-end round-trip from the live Google Books API into a real Postgres via Flyway-managed
 * schema, then back out through the repository.
 *
 * <p>Skipped unless {@code GOOGLE_BOOKS_API_KEY} is in the environment so CI without the key
 * stays green. The container also requires Docker; standard for the project's other Testcontainer
 * suites.
 *
 * <p>The two cases cover the failure modes a code change to migrations, entities, or
 * {@code CatalogService} would actually introduce: a missing V20 column or wrong column name in
 * the entity, a Google ISBN that the V9 check constraint rejects, an upsert that inserts twice
 * because the lookup-by-volume-id step is wrong, and a duplicate author insert.
 */
@SpringBootTest(properties = {
    "googlebooks.base-url=https://www.googleapis.com/books/v1",
    "googlebooks.connect-timeout=5000",
    "googlebooks.read-timeout=15000"
})
@Testcontainers
@EnabledIfEnvironmentVariable(named = "GOOGLE_BOOKS_API_KEY", matches = ".+")
class CatalogGoogleBooksPersistenceIntegrationTest {

    private static final String EYE_OF_THE_WORLD_AUTHOR = "Robert Jordan";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17"));

    @Autowired
    private GoogleBooksClient googleBooksClient;

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
    @DisplayName("upserting Eye of the World writes book, author, and join rows readable by the repository")
    void upsertEyeOfTheWorldLandsCleanlyInPostgres() {
        final SourceBook source = fetchEyeOfTheWorld();

        final Book persisted = catalogService.upsertFromSource(source);

        final String volumeId = Objects.requireNonNull(persisted.getGoogleBooksVolumeId(),
            "upsert must have written the source volume id onto the entity");
        final Optional<Book> reloaded = bookRepository.findByGoogleBooksVolumeId(volumeId);
        assertThat(reloaded)
            .as("repository must find by the same volume id the upsert wrote")
            .isPresent()
            .get()
            .satisfies(book -> {
                assertThat(book.getTitle()).containsIgnoringCase("Eye of the World");
                assertThat(book.getGoogleBooksVolumeId()).isEqualTo(source.googleBooksVolumeId());
                assertThat(book.getFirstPublishYear()).isEqualTo(source.publicationYear());
                assertThat(book.getAuthors())
                    .as("M2M join row must exist; Robert Jordan must be the sole stored author")
                    .extracting("name")
                    .containsExactly(EYE_OF_THE_WORLD_AUTHOR);
            });
        assertThat(authorRepository.findByName(EYE_OF_THE_WORLD_AUTHOR))
            .as("author row visible independently of the book lookup")
            .isPresent();
    }

    @Test
    @DisplayName("re-upserting the same SourceBook updates in place rather than inserting again")
    void upsertIsIdempotentForSameVolume() {
        final SourceBook source = fetchEyeOfTheWorld();

        final Book first = catalogService.upsertFromSource(source);
        final OffsetDateTime firstUpdatedAt = first.getUpdatedAt();
        final long firstId = first.getBookId();

        final Book second = catalogService.upsertFromSource(source);

        assertThat(second.getBookId())
            .as("second upsert must reuse the existing book_id, not allocate a new one")
            .isEqualTo(firstId);
        assertThat(bookRepository.count())
            .as("re-upserting the same volume must not duplicate the book row")
            .isEqualTo(1L);
        assertThat(authorRepository.count())
            .as("re-upserting must not insert a second 'Robert Jordan' row")
            .isEqualTo(1L);
        assertThat(second.getUpdatedAt())
            .as("@PreUpdate must fire on the second save so updated_at moves forward")
            .isAfterOrEqualTo(firstUpdatedAt);
    }

    private SourceBook fetchEyeOfTheWorld() {
        final Optional<SourceBook> source = googleBooksClient.fetchByTitleAuthor(
            "The Eye of the World", EYE_OF_THE_WORLD_AUTHOR);
        assertThat(source)
            .as("Google Books must know this book; the live client test covers data-quality details")
            .isPresent();
        return source.get();
    }
}
