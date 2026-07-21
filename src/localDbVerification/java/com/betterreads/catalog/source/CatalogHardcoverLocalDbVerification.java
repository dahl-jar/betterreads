package com.betterreads.catalog.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import com.betterreads.catalog.entity.Book;
import com.betterreads.catalog.repository.AuthorRepository;
import com.betterreads.catalog.repository.BookRepository;
import com.betterreads.catalog.service.write.BookUpsertService;
import com.betterreads.catalog.service.source.model.SourceBook;
import com.betterreads.integration.hardcover.HardcoverClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Persists live Hardcover ratings and series data in local Postgres for operator inspection. */
@SpringBootTest(properties = {
    "hardcover.base-url=https://api.hardcover.app/v1/graphql",
    "hardcover.bearer-token=${HARDCOVER_BEARER_TOKEN:}",
    "hardcover.connect-timeout=5000",
    "hardcover.read-timeout=15000"
})
@EnabledIfEnvironmentVariable(named = "RUN_LOCAL_DB_VERIFICATION", matches = "1")
@EnabledIfEnvironmentVariable(named = "HARDCOVER_BEARER_TOKEN", matches = ".+")
// PMD.ClassNamingConventions: operator-run database verification without a Test suffix
@SuppressWarnings("PMD.ClassNamingConventions")
class CatalogHardcoverLocalDbVerification {

    @Autowired
    private HardcoverClient hardcoverClient;

    @Autowired
    private BookUpsertService bookUpsertService;

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

    @ParameterizedTest(name = "{0} persists with its Hardcover id, average rating, and rating count")
    @MethodSource("slate")
    @DisplayName("the slate persists with Hardcover rating metadata")
    void slatePersistsRatingsToLocalDatabase(final String title, final String author) {
        final Optional<SourceBook> source = hardcoverClient.fetchByTitleAuthor(title, author);
        assertThat(source).as("Hardcover knows %s", title).isPresent();

        final Book persisted = bookUpsertService.upsertFromSource(source.get());
        final String hardcoverId = Objects.requireNonNull(
            persisted.getHardcoverId(), "persisted book has a Hardcover id");

        assertThat(bookRepository.findByHardcoverId(hardcoverId))
            .isPresent()
            .get()
            .satisfies(book -> {
                assertThat(book.getAverageRating()).isNotNull();
                assertThat(book.getRatingCount()).isNotNull();
            });
    }
}
