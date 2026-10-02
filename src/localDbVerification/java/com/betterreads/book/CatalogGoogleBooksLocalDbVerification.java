package com.betterreads.book;

import static com.betterreads.book.CatalogUpserts.assertIsEyeOfTheWorld;
import static com.betterreads.book.CatalogUpserts.fetchEyeOfTheWorld;

import com.betterreads.booksource.SourceBook;
import com.betterreads.clients.googlebooks.GoogleBooksClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Persists live Google Books data in local Postgres for operator inspection. */
@SpringBootTest(properties = {
    "googlebooks.base-url=https://www.googleapis.com/books/v1",
    "googlebooks.connect-timeout=5000",
    "googlebooks.read-timeout=15000"
})
@EnabledIfEnvironmentVariable(named = "RUN_LOCAL_DB_VERIFICATION", matches = "1")
@EnabledIfEnvironmentVariable(named = "GOOGLE_BOOKS_API_KEY", matches = ".+")
@Import(CatalogUpserts.class)
// PMD.ClassNamingConventions: operator-run database verification without a Test suffix
@SuppressWarnings("PMD.ClassNamingConventions")
class CatalogGoogleBooksLocalDbVerification {

    @Autowired
    private CatalogUpserts catalogUpserts;

    @Autowired
    private GoogleBooksClient googleBooksClient;

    @BeforeEach
    void clearCatalog() {
        catalogUpserts.clear();
    }

    @Test
    @DisplayName("Eye of the World persists with its Google Books metadata")
    void eyeOfTheWorldPersistsToLocalDatabase() {
        final SourceBook source = fetchEyeOfTheWorld(googleBooksClient);

        final Book reloaded = catalogUpserts.upsertAndReloadByVolumeId(source);

        assertIsEyeOfTheWorld(reloaded);
    }
}
