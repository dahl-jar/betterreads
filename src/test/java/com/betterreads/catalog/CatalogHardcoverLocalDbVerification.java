package com.betterreads.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.stream.Stream;

import com.betterreads.catalog.entity.Book;
import com.betterreads.catalog.repository.AuthorRepository;
import com.betterreads.catalog.repository.BookRepository;
import com.betterreads.catalog.service.CatalogService;
import com.betterreads.catalog.service.SourceBook;
import com.betterreads.integration.hardcover.HardcoverClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Persists the six-book slate fetched from the live Hardcover API into the local compose Postgres so
 * the rating, rating count, and series columns stay inspectable with {@code psql} after the JVM exits.
 *
 * <p>Skipped unless {@code RUN_LOCAL_DB_VERIFICATION=1} AND {@code HARDCOVER_BEARER_TOKEN} are set;
 * opt-in because it writes to a real long-lived database. Run with:
 * <pre>
 *   docker compose up -d db
 *   source .env &amp;&amp; RUN_LOCAL_DB_VERIFICATION=1 ./gradlew test \
 *     --tests '*CatalogHardcoverLocalDbVerification*'
 *   docker exec -it betterreads-db psql -U betterreads_app -d betterreads \
 *     -c "SELECT title, average_rating, rating_count, series_name, series_position FROM book;"
 * </pre>
 */
@SpringBootTest(properties = {
    "hardcover.base-url=https://api.hardcover.app/v1/graphql",
    "hardcover.bearer-token=${HARDCOVER_BEARER_TOKEN:}",
    "hardcover.connect-timeout=5000",
    "hardcover.read-timeout=15000"
})
@EnabledIfEnvironmentVariable(named = "RUN_LOCAL_DB_VERIFICATION", matches = "1")
@EnabledIfEnvironmentVariable(named = "HARDCOVER_BEARER_TOKEN", matches = ".+")
// PMD.ClassNamingConventions: not *Test on purpose, an opt-in manual DB verification, not a CI test
@SuppressWarnings("PMD.ClassNamingConventions")
class CatalogHardcoverLocalDbVerification {

    @Autowired
    private HardcoverClient hardcoverClient;

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private AuthorRepository authorRepository;

    static Stream<Arguments> slate() {
        return Stream.of(
            Arguments.of("A Clash of Kings", "George R.R. Martin"),
            Arguments.of("The Hobbit", "J.R.R. Tolkien"),
            Arguments.of("Dune", "Frank Herbert"),
            Arguments.of("The Sandman", "Neil Gaiman"),
            Arguments.of("Watchmen", "Alan Moore")
        );
    }

    @BeforeEach
    void clearCatalog() {
        bookRepository.deleteAll();
        authorRepository.deleteAll();
    }

    @ParameterizedTest(name = "{0} persists with its Hardcover id and reader-signal columns")
    @MethodSource("slate")
    @DisplayName("the slate lands in the local compose Postgres for psql inspection")
    void slatePersistsToLocalCompose(final String title, final String author) {
        final Optional<SourceBook> source = hardcoverClient.fetchByTitleAuthor(title, author);
        assertThat(source).as("Hardcover knows %s", title).isPresent();

        final Book persisted = catalogService.upsertFromSource(source.get());

        assertThat(bookRepository.findByHardcoverId(persisted.getHardcoverId()))
            .isPresent()
            .get()
            .satisfies(book -> {
                assertThat(book.getAverageRating()).isNotNull();
                assertThat(book.getRatingCount()).isNotNull();
            });
    }
}
