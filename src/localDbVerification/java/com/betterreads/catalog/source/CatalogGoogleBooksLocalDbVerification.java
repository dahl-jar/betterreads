package com.betterreads.catalog.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import java.util.Optional;

import com.betterreads.catalog.entity.Book;
import com.betterreads.catalog.repository.AuthorRepository;
import com.betterreads.catalog.repository.BookRepository;
import com.betterreads.catalog.service.write.BookUpsertService;
import com.betterreads.catalog.service.source.model.SourceBook;
import com.betterreads.integration.googlebooks.GoogleBooksClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Persists live Google Books data in local Postgres for operator inspection. */
@SpringBootTest(properties = {
    "googlebooks.base-url=https://www.googleapis.com/books/v1",
    "googlebooks.connect-timeout=5000",
    "googlebooks.read-timeout=15000"
})
@EnabledIfEnvironmentVariable(named = "RUN_LOCAL_DB_VERIFICATION", matches = "1")
@EnabledIfEnvironmentVariable(named = "GOOGLE_BOOKS_API_KEY", matches = ".+")
// PMD.ClassNamingConventions: operator-run database verification without a Test suffix
@SuppressWarnings("PMD.ClassNamingConventions")
class CatalogGoogleBooksLocalDbVerification {

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
    @DisplayName("Eye of the World persists with its Google Books metadata")
    void eyeOfTheWorldPersistsToLocalDatabase() {
        final Optional<SourceBook> source = googleBooksClient.fetchByTitleAuthor(
            "The Eye of the World", EYE_OF_THE_WORLD_AUTHOR);
        assertThat(source).isPresent();

        final Book persisted = bookUpsertService.upsertFromSource(source.get());

        final String volumeId = Objects.requireNonNull(persisted.getGoogleBooksVolumeId(),
            "upsert must have written the source volume id onto the entity");
        final Optional<Book> reloaded = bookRepository.findByGoogleBooksVolumeId(volumeId);
        assertThat(reloaded)
            .isPresent()
            .get()
            .satisfies(book -> {
                assertThat(book.getTitle()).containsIgnoringCase("Eye of the World");
                assertThat(book.getAuthors()).extracting("name").containsExactly(EYE_OF_THE_WORLD_AUTHOR);
            });
    }
}
