package com.betterreads.catalog;

import com.betterreads.support.ContainerizedTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.betterreads.catalog.entity.Author;
import com.betterreads.catalog.entity.BookAward;
import com.betterreads.catalog.repository.AuthorRepository;
import com.betterreads.catalog.repository.BookRepository;
import com.betterreads.catalog.service.BookFieldSource;
import com.betterreads.catalog.service.CatalogService;
import com.betterreads.catalog.service.SourceAuthor;
import com.betterreads.catalog.service.SourceBook;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Round-trips a Wikidata {@link SourceBook} through {@code CatalogService} into a real Postgres and
 * reads the award rows and author identity columns back from the database.
 */
@SpringBootTest(properties = "jwt.secret=test-secret-at-least-thirty-two-bytes-long")
@Testcontainers
class CatalogWikidataPersistenceIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String DUNE_QID = "Q190192";
    private static final String HERBERT_NAME = "Frank Herbert";
    private static final String HERBERT_QID = "Q7934";
    private static final String HERBERT_PHOTO =
        "https://commons.wikimedia.org/wiki/Special:FilePath/Frank Herbert 1984.jpg";
    private static final String HERBERT_BIO = "https://en.wikipedia.org/wiki/Frank_Herbert";
    private static final String HUGO = "Hugo Award for Best Novel";
    private static final String NEBULA = "Nebula Award for Best Novel";

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

    private static SourceAuthor herbert() {
        return new SourceAuthor(HERBERT_NAME, HERBERT_QID, HERBERT_PHOTO, HERBERT_BIO);
    }

    private static SourceBook dune(final @Nullable List<String> awards) {
        return SourceBook.builder(BookFieldSource.WIKIDATA)
            .wikidataQid(DUNE_QID)
            .title("Dune")
            .authors(List.of(herbert()))
            .awards(awards)
            .build();
    }

    @Nested
    class Awards {

        @Test
        void persistsAwardRowsReadableFromTheDatabase() {
            catalogService.upsertFromSource(dune(List.of(NEBULA, HUGO)));

            assertThat(bookRepository.findWithAwardsByWikidataQid(DUNE_QID))
                .isPresent()
                .get()
                .satisfies(book -> assertThat(book.getAwards())
                    .extracting(BookAward::getAward)
                    .containsExactlyInAnyOrder(NEBULA, HUGO));
        }

        @Test
        void leavesExistingAwardsWhenARefreshCarriesNull() {
            catalogService.upsertFromSource(dune(List.of(HUGO)));

            catalogService.upsertFromSource(dune(null));

            assertThat(reloadedAwards()).containsExactly(HUGO);
        }

        @Test
        void clearsAwardsWhenARefreshCarriesAnEmptyList() {
            catalogService.upsertFromSource(dune(List.of(HUGO)));

            catalogService.upsertFromSource(dune(List.of()));

            assertThat(reloadedAwards()).isEmpty();
        }

        private List<String> reloadedAwards() {
            return bookRepository.findWithAwardsByWikidataQid(DUNE_QID).orElseThrow()
                .getAwards().stream().map(BookAward::getAward).toList();
        }
    }

    @Nested
    class AuthorIdentity {

        @Test
        void persistsThePhotoAndBioOntoTheAuthorRow() {
            catalogService.upsertFromSource(dune(List.of()));

            assertThat(authorRepository.findByWikidataQid(HERBERT_QID))
                .isPresent()
                .get()
                .satisfies(author -> {
                    assertThat(author.getName()).isEqualTo(HERBERT_NAME);
                    assertThat(author.getPhotoUrl()).isEqualTo(HERBERT_PHOTO);
                    assertThat(author.getBio()).isEqualTo(HERBERT_BIO);
                });
        }

        @Test
        void reusesTheAuthorMatchedByQidWhenTheNameAlsoExists() {
            final Author existing = new Author();
            existing.setName(HERBERT_NAME);
            authorRepository.saveAndFlush(existing);

            catalogService.upsertFromSource(dune(List.of()));

            assertThat(authorRepository.count())
                .as("the QID lookup must reuse the existing name row, not insert a second")
                .isEqualTo(1L);
            assertThat(authorRepository.findByWikidataQid(HERBERT_QID))
                .get()
                .extracting(Author::getPhotoUrl)
                .isNotNull();
        }
    }
}
