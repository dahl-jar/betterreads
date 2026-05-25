package com.betterreads.catalog;

import static org.assertj.core.api.Assertions.assertThat;

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

/**
 * Same round-trip as {@link CatalogGoogleBooksPersistenceIntegrationTest}, but against the
 * locally-running compose Postgres so the result stays inspectable with {@code psql} after
 * the JVM exits.
 *
 * <p>Skipped unless {@code RUN_LOCAL_DB_VERIFICATION=1} AND {@code GOOGLE_BOOKS_API_KEY} are
 * set; opt-in because it writes to a real long-lived database. Run with:
 * <pre>
 *   docker compose up -d db
 *   source .env && RUN_LOCAL_DB_VERIFICATION=1 ./gradlew test \
 *     --tests '*CatalogGoogleBooksLocalDbVerification*'
 *   docker exec -it betterreads-db psql -U betterreads -d betterreads \
 *     -c "SELECT title, first_publish_year, google_books_volume_id FROM book;"
 * </pre>
 */
@SpringBootTest(properties = {
    "googlebooks.base-url=https://www.googleapis.com/books/v1",
    "googlebooks.connect-timeout=5000",
    "googlebooks.read-timeout=15000"
})
@EnabledIfEnvironmentVariable(named = "RUN_LOCAL_DB_VERIFICATION", matches = "1")
@EnabledIfEnvironmentVariable(named = "GOOGLE_BOOKS_API_KEY", matches = ".+")
class CatalogGoogleBooksLocalDbVerification {

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
    @DisplayName("Eye of the World lands in the local compose Postgres for psql inspection")
    void eyeOfTheWorldLandsInLocalCompose() {
        final Optional<SourceBook> source = googleBooksClient.fetchByTitleAuthor(
            "The Eye of the World", "Robert Jordan");
        assertThat(source).isPresent();

        final Book persisted = catalogService.upsertFromSource(source.get());

        final String volumeId = Objects.requireNonNull(persisted.getGoogleBooksVolumeId(),
            "upsert must have written the source volume id onto the entity");
        final Optional<Book> reloaded = bookRepository.findByGoogleBooksVolumeId(volumeId);
        assertThat(reloaded)
            .isPresent()
            .get()
            .satisfies(book -> {
                assertThat(book.getTitle()).containsIgnoringCase("Eye of the World");
                assertThat(book.getAuthors()).extracting("name").containsExactly("Robert Jordan");
            });
    }
}
