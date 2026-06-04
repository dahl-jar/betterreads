package com.betterreads.catalog.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.stream.Stream;

import com.betterreads.catalog.entity.Book;
import com.betterreads.catalog.repository.AuthorRepository;
import com.betterreads.catalog.repository.BookRepository;
import com.betterreads.catalog.service.read.CatalogService;
import com.betterreads.catalog.service.source.SourceBook;
import com.betterreads.integration.wikidata.WikidataClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Persists the slate fetched from the live Wikidata API into the local compose Postgres so the
 * award rows, series, and author photo and bio columns stay inspectable with {@code psql} after the
 * JVM exits.
 *
 * <p>Skipped unless {@code RUN_LOCAL_DB_VERIFICATION=1}; opt-in because it writes to a real
 * long-lived database. Run with:
 * <pre>
 *   docker compose up -d db
 *   source .env &amp;&amp; RUN_LOCAL_DB_VERIFICATION=1 ./gradlew test \
 *     --tests '*CatalogWikidataLocalDbVerification*'
 *   docker exec -it betterreads-db psql -U betterreads_app -d betterreads \
 *     -c "SELECT b.title, b.series_name, a.name, a.photo_url, a.bio FROM book b
 *         JOIN book_author ba ON ba.book_id = b.book_id
 *         JOIN author a ON a.author_id = ba.author_id;"
 *   docker exec -it betterreads-db psql -U betterreads_app -d betterreads \
 *     -c "SELECT b.title, w.award FROM book b JOIN book_award w ON w.book_id = b.book_id;"
 * </pre>
 */
@SpringBootTest(properties = {
    "wikidata.base-url=https://www.wikidata.org",
    "wikidata.connect-timeout=5000",
    "wikidata.read-timeout=25000"
})
@EnabledIfEnvironmentVariable(named = "RUN_LOCAL_DB_VERIFICATION", matches = "1")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
// PMD.ClassNamingConventions: not *Test on purpose, an opt-in manual DB verification, not a CI test
@SuppressWarnings("PMD.ClassNamingConventions")
class CatalogWikidataLocalDbVerification {

    @Autowired
    private WikidataClient wikidataClient;

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private AuthorRepository authorRepository;

    static Stream<Arguments> slate() {
        return Stream.of(
            Arguments.of("Dune", "Frank Herbert"),
            Arguments.of("A Clash of Kings", "George R. R. Martin"),
            Arguments.of("The Hobbit", "J. R. R. Tolkien"),
            Arguments.of("The Eye of the World", "Robert Jordan"),
            Arguments.of("The Sandman", "Neil Gaiman"),
            Arguments.of("Watchmen", "Alan Moore")
        );
    }

    @BeforeAll
    void clearCatalog() {
        bookRepository.deleteAll();
        authorRepository.deleteAll();
    }

    @ParameterizedTest(name = "{0} persists with its Wikidata id, awards, and author identity")
    @MethodSource("slate")
    @DisplayName("the slate lands in the local compose Postgres for psql inspection")
    void slatePersistsToLocalCompose(final String title, final String author) {
        final Optional<SourceBook> source = wikidataClient.fetchByTitleAuthor(title, author);
        assertThat(source).as("Wikidata knows %s", title).isPresent();

        final Book persisted = catalogService.upsertFromSource(source.get());

        assertThat(bookRepository.findWithAwardsByWikidataQid(persisted.getWikidataQid()))
            .isPresent()
            .get()
            .satisfies(book -> assertThat(book.getTitle()).isEqualTo(title));
        assertThat(authorRepository.findByName(author))
            .isPresent()
            .get()
            .satisfies(stored -> {
                assertThat(stored.getWikidataQid()).isNotNull();
                assertThat(stored.getPhotoUrl()).isNotNull();
                assertThat(stored.getBio()).isNotNull();
            });
    }
}
