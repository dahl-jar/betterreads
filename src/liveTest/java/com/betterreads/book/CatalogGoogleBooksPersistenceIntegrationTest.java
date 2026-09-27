package com.betterreads.book;

import com.betterreads.booksource.SourceBook;
import com.betterreads.clients.googlebooks.GoogleBooksClient;
import com.betterreads.testsupport.ContainerizedTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Persists live Google Books data through the Flyway-managed Postgres schema. */
@SpringBootTest(properties = {
    "googlebooks.base-url=https://www.googleapis.com/books/v1",
    "googlebooks.connect-timeout=5000",
    "googlebooks.read-timeout=15000"
})
@Testcontainers
@EnabledIfEnvironmentVariable(named = "GOOGLE_BOOKS_API_KEY", matches = ".+")
class CatalogGoogleBooksPersistenceIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String EYE_OF_THE_WORLD_AUTHOR = "Robert Jordan";

    @Autowired
    private GoogleBooksClient googleBooksClient;

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
    @DisplayName("upserting Eye of the World persists its book, author, and join rows")
    void upsertEyeOfTheWorldPersistsRelationships() {
        final SourceBook source = fetchEyeOfTheWorld();

        final Book persisted = bookUpsertService.upsertFromSource(source);

        final String volumeId = Objects.requireNonNull(persisted.getGoogleBooksVolumeId(),
            "upsert left the source volume id unset");
        final Optional<Book> reloaded = bookRepository.findByGoogleBooksVolumeId(volumeId);
        assertThat(reloaded)
            .as("repository finds the persisted volume id")
            .isPresent()
            .get()
            .satisfies(book -> {
                assertThat(book.getTitle()).containsIgnoringCase("Eye of the World");
                assertThat(book.getGoogleBooksVolumeId()).isEqualTo(source.googleBooksVolumeId());
                assertThat(book.getFirstPublishYear()).isEqualTo(source.publicationYear());
                assertThat(book.getAuthors())
                    .as("stored book has Robert Jordan as its sole author")
                    .extracting("name")
                    .containsExactly(EYE_OF_THE_WORLD_AUTHOR);
            });
        assertThat(authorRepository.findByName(EYE_OF_THE_WORLD_AUTHOR))
            .as("author row remains independently readable")
            .isPresent();
    }

    @Test
    @DisplayName("re-upserting the same volume reuses the existing rows")
    void upsertIsIdempotentForSameVolume() {
        final SourceBook source = fetchEyeOfTheWorld();

        final Book first = bookUpsertService.upsertFromSource(source);
        final long firstId = first.getBookId();

        final Book second = bookUpsertService.upsertFromSource(source);

        assertThat(second.getBookId())
            .as("second upsert reuses the existing book_id")
            .isEqualTo(firstId);
        assertThat(bookRepository.count())
            .as("book row count remains one after re-upsert")
            .isEqualTo(1L);
        assertThat(authorRepository.count())
            .as("author row count remains one after re-upsert")
            .isEqualTo(1L);
    }

    private SourceBook fetchEyeOfTheWorld() {
        final Optional<SourceBook> source = googleBooksClient.fetchByTitleAuthor(
            "The Eye of the World", EYE_OF_THE_WORLD_AUTHOR);
        assertThat(source)
            .as("Google Books returns Eye of the World")
            .isPresent();
        return source.get();
    }
}
