package com.betterreads.catalog.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.stream.Stream;

import com.betterreads.catalog.repository.AuthorRepository;
import com.betterreads.catalog.repository.BookRepository;
import com.betterreads.catalog.service.read.CatalogService;
import com.betterreads.catalog.service.source.SourceBook;
import com.betterreads.integration.loc.LocClient;
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
 * Fetches the slate from the live LoC SRU endpoint by LCCN and persists it into the local compose
 * Postgres so the {@code loc_lccn} column and the MODS-derived fields stay inspectable with
 * {@code psql} after the JVM exits.
 *
 * <p>Skipped unless {@code RUN_LOCAL_DB_VERIFICATION=1} and {@code RUN_LOC_LIVE=1} are set; opt-in
 * because it writes to a real long-lived database and reaches the network. Run with:
 * <pre>
 *   docker compose -f docker/docker-compose.yml --env-file .env up -d db
 *   RUN_LOCAL_DB_VERIFICATION=1 RUN_LOC_LIVE=1 ./gradlew test \
 *     --tests '*CatalogLocLocalDbVerification*'
 *   docker exec -it betterreads-db psql -U betterreads_app -d betterreads \
 *     -c "SELECT title, loc_lccn, isbn, first_publish_year, page_count FROM book;"
 * </pre>
 */
@SpringBootTest(properties = {
    "loc.base-url=http://lx2.loc.gov:210/lcdb",
    "loc.connect-timeout=5000",
    "loc.read-timeout=15000"
})
@EnabledIfEnvironmentVariable(named = "RUN_LOCAL_DB_VERIFICATION", matches = "1")
@EnabledIfEnvironmentVariable(named = "RUN_LOC_LIVE", matches = "1")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
// PMD.ClassNamingConventions: not *Test on purpose, an opt-in manual DB verification, not a CI test
@SuppressWarnings("PMD.ClassNamingConventions")
class CatalogLocLocalDbVerification {

    @Autowired
    private LocClient locClient;

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private AuthorRepository authorRepository;

    static Stream<Arguments> slate() {
        return Stream.of(
            Arguments.of("2019287107", "Dune"),
            Arguments.of("2013003992", "Watchmen"),
            Arguments.of("2024442463", "hobbit"),
            Arguments.of("92159876", "Sandman"),
            Arguments.of("89007939", "eye of the world"),
            Arguments.of("98037954", "clash of kings"));
    }

    @BeforeAll
    void clearCatalog() {
        bookRepository.deleteAll();
        authorRepository.deleteAll();
    }

    @ParameterizedTest(name = "{1} (lccn {0}) persists with its loc_lccn")
    @MethodSource("slate")
    @DisplayName("the slate lands in the local compose Postgres for psql inspection")
    void slatePersistsToLocalCompose(final String lccn, final String titleFragment) {
        final Optional<SourceBook> source = locClient.fetchByLccn(lccn);
        assertThat(source).as("LoC knows lccn %s", lccn).isPresent();

        catalogService.upsertFromSource(source.get());

        assertThat(bookRepository.findByLocLccn(lccn))
            .isPresent()
            .get()
            .satisfies(book -> {
                assertThat(book.getLocLccn()).isEqualTo(lccn);
                assertThat(book.getTitle()).containsIgnoringCase(titleFragment);
            });
    }
}
