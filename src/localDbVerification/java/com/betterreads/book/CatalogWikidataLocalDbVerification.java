package com.betterreads.book;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import com.betterreads.booksource.SourceAuthor;
import com.betterreads.booksource.SourceBook;
import com.betterreads.clients.wikidata.WikidataClient;
import com.betterreads.text.AuthorNames;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Persists live Wikidata book and author metadata in local Postgres for operator inspection. */
@SpringBootTest(properties = {
    "wikidata.base-url=https://www.wikidata.org",
    "wikidata.connect-timeout=5000",
    "wikidata.read-timeout=25000"
})
@EnabledIfEnvironmentVariable(named = "RUN_LOCAL_DB_VERIFICATION", matches = "1")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
// PMD.ClassNamingConventions: operator-run database verification without a Test suffix
@SuppressWarnings("PMD.ClassNamingConventions")
class CatalogWikidataLocalDbVerification {

    @Autowired
    private WikidataClient wikidataClient;

    @Autowired
    private BookUpsertService bookUpsertService;

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

    @ParameterizedTest(name = "{0} persists with its Wikidata id and author fields")
    @MethodSource("slate")
    @DisplayName("the slate persists with Wikidata book and author metadata")
    void slatePersistsWikidataFieldsToLocalDatabase(final String title, final String author) {
        final Optional<SourceBook> source = wikidataClient.fetchByTitleAuthor(title, author);
        assertThat(source).as("Wikidata knows %s", title).isPresent();
        final SourceAuthor sourceAuthor = Objects.requireNonNull(
                source.get().authors(), "Wikidata returned no authors")
            .stream()
            .filter(candidate -> candidate.name().equals(author))
            .findFirst()
            .orElseThrow();

        final Book persisted = bookUpsertService.upsertFromSource(source.get());
        final String wikidataQid = Objects.requireNonNull(
            persisted.getWikidataQid(), "persisted book has no Wikidata id");

        assertThat(bookRepository.findByWikidataQid(wikidataQid))
            .isPresent()
            .get()
            .satisfies(book -> assertThat(book.getTitle()).isEqualTo(title));
        assertThat(authorRepository.findByNameKey(AuthorNames.key(author)))
            .isPresent()
            .get()
            .satisfies(stored -> {
                assertThat(stored.getWikidataQid()).isEqualTo(sourceAuthor.wikidataQid());
                assertThat(stored.getPhotoUrl()).isEqualTo(sourceAuthor.photoUrl());
                assertThat(stored.getBio()).isEqualTo(sourceAuthor.bio());
            });
    }
}
