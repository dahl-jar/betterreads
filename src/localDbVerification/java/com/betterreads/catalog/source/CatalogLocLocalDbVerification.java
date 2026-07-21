package com.betterreads.catalog.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.stream.Stream;

import com.betterreads.catalog.repository.AuthorRepository;
import com.betterreads.catalog.repository.BookRepository;
import com.betterreads.catalog.service.write.BookUpsertService;
import com.betterreads.catalog.service.source.model.SourceBook;
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

/** Persists live Library of Congress metadata in local Postgres for operator inspection. */
@SpringBootTest(properties = {
    "loc.base-url=http://lx2.loc.gov:210/lcdb",
    "loc.connect-timeout=5000",
    "loc.read-timeout=15000"
})
@EnabledIfEnvironmentVariable(named = "RUN_LOCAL_DB_VERIFICATION", matches = "1")
@EnabledIfEnvironmentVariable(named = "RUN_LOC_LIVE", matches = "1")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
// PMD.ClassNamingConventions: operator-run database verification without a Test suffix
@SuppressWarnings("PMD.ClassNamingConventions")
class CatalogLocLocalDbVerification {

    @Autowired
    private LocClient locClient;

    @Autowired
    private BookUpsertService bookUpsertService;

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
    @DisplayName("the slate persists with Library of Congress metadata")
    void slatePersistsLocFieldsToLocalDatabase(final String lccn, final String titleFragment) {
        final Optional<SourceBook> source = locClient.fetchByLccn(lccn);
        assertThat(source).as("LoC knows lccn %s", lccn).isPresent();

        bookUpsertService.upsertFromSource(source.get());

        assertThat(bookRepository.findByLocLccn(lccn))
            .isPresent()
            .get()
            .satisfies(book -> {
                assertThat(book.getLocLccn()).isEqualTo(lccn);
                assertThat(book.getTitle()).containsIgnoringCase(titleFragment);
            });
    }
}
