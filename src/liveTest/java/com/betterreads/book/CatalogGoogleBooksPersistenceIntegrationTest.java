package com.betterreads.book;

import com.betterreads.booksource.SourceBook;
import com.betterreads.clients.googlebooks.GoogleBooksClient;
import com.betterreads.testsupport.ContainerizedTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static com.betterreads.book.CatalogUpserts.EYE_OF_THE_WORLD_AUTHOR;
import static com.betterreads.book.CatalogUpserts.assertIsEyeOfTheWorld;
import static com.betterreads.book.CatalogUpserts.fetchEyeOfTheWorld;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Persists live Google Books data through the Flyway-managed Postgres schema. */
@SpringBootTest(properties = {
    "googlebooks.base-url=https://www.googleapis.com/books/v1",
    "googlebooks.connect-timeout=5000",
    "googlebooks.read-timeout=15000"
})
@Testcontainers
@EnabledIfEnvironmentVariable(named = "GOOGLE_BOOKS_API_KEY", matches = ".+")
@Import(CatalogUpserts.class)
class CatalogGoogleBooksPersistenceIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    @Autowired
    private CatalogUpserts catalogUpserts;

    @Autowired
    private GoogleBooksClient googleBooksClient;

    @Autowired
    private AuthorRepository authorRepository;

    @BeforeEach
    void clearCatalog() {
        catalogUpserts.clear();
    }

    @Test
    @DisplayName("upserting Eye of the World persists its book, author, and join rows")
    void upsertEyeOfTheWorldPersistsRelationships() {
        final SourceBook source = fetchEyeOfTheWorld(googleBooksClient);

        final Book reloaded = catalogUpserts.upsertAndReloadByVolumeId(source);

        assertIsEyeOfTheWorld(reloaded);
        assertThat(reloaded)
            .satisfies(book -> {
                assertThat(book.getGoogleBooksVolumeId()).isEqualTo(source.googleBooksVolumeId());
                assertThat(book.getFirstPublishYear()).isEqualTo(source.publicationYear());
            });
        assertThat(authorRepository.findByName(EYE_OF_THE_WORLD_AUTHOR))
            .as("author row remains independently readable")
            .isPresent();
    }

    @Test
    @DisplayName("re-upserting the same volume reuses the existing rows")
    void upsertIsIdempotentForSameVolume() {
        final SourceBook source = fetchEyeOfTheWorld(googleBooksClient);

        catalogUpserts.upsertTwiceKeepingOneBook(source);

        assertThat(authorRepository.count())
            .as("author row count remains one after re-upsert")
            .isEqualTo(1L);
    }
}
