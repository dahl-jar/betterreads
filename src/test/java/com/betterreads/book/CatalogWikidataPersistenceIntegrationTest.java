package com.betterreads.book;

import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.SourceAuthor;
import com.betterreads.booksource.SourceBook;
import com.betterreads.testsupport.ContainerizedTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Wikidata awards and author identity round-tripped through a real Postgres. */
@SpringBootTest
@Testcontainers
class CatalogWikidataPersistenceIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String RED_RISING_QID = "Q1";
    private static final String RED_RISING_TITLE = "Red Rising";
    private static final String DARROW_NAME = "Darrow";
    private static final String REAPER_NAME = "Reaper";
    private static final String DARROW_QID = "Q2";
    private static final String DARROW_PHOTO = "https://photos.example.test/darrow.jpg";
    private static final String DARROW_BIO = "https://wiki.example.test/Darrow";
    private static final String HUGO = "Hugo Award for Best Novel";
    private static final String NEBULA = "Nebula Award for Best Novel";

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

    private static SourceAuthor darrow() {
        return new SourceAuthor(DARROW_NAME, DARROW_QID, DARROW_PHOTO, DARROW_BIO);
    }

    private static SourceBook redRising(final @Nullable List<String> awards) {
        return SourceBook.builder(BookFieldSource.WIKIDATA)
            .wikidataQid(RED_RISING_QID)
            .title(RED_RISING_TITLE)
            .authors(List.of(darrow()))
            .awards(awards)
            .build();
    }

    private static SourceBook redRisingBy(final @Nullable List<SourceAuthor> authors) {
        return SourceBook.builder(BookFieldSource.WIKIDATA)
            .wikidataQid(RED_RISING_QID)
            .title(RED_RISING_TITLE)
            .authors(authors)
            .build();
    }

    @Nested
    class Awards {

        @Test
        void persistsAwardRowsReadableFromTheDatabase() {
            bookUpsertService.upsertFromSource(redRising(List.of(NEBULA, HUGO)));

            assertThat(bookRepository.findWithAwardsByWikidataQid(RED_RISING_QID))
                .isPresent()
                .get()
                .satisfies(book -> assertThat(book.getAwards())
                    .extracting(BookAward::getAward)
                    .containsExactlyInAnyOrder(NEBULA, HUGO));
        }

        @Test
        void leavesExistingAwardsWhenARefreshCarriesNull() {
            bookUpsertService.upsertFromSource(redRising(List.of(HUGO)));

            bookUpsertService.upsertFromSource(redRising(null));

            assertThat(reloadedAwards()).containsExactly(HUGO);
        }

        @Test
        void clearsAwardsWhenARefreshCarriesAnEmptyList() {
            bookUpsertService.upsertFromSource(redRising(List.of(HUGO)));

            bookUpsertService.upsertFromSource(redRising(List.of()));

            assertThat(reloadedAwards()).isEmpty();
        }

        private List<String> reloadedAwards() {
            final Book book = bookRepository.findWithAwardsByWikidataQid(RED_RISING_QID).orElseThrow();
            return book.getAwards().stream().map(BookAward::getAward).toList();
        }
    }

    @Nested
    class AuthorIdentity {

        @Test
        void persistsThePhotoAndBioOntoTheAuthorRow() {
            bookUpsertService.upsertFromSource(redRising(List.of()));

            assertThat(authorRepository.findByWikidataQid(DARROW_QID))
                .isPresent()
                .get()
                .satisfies(author -> {
                    assertThat(author.getName()).isEqualTo(DARROW_NAME);
                    assertThat(author.getPhotoUrl()).isEqualTo(DARROW_PHOTO);
                    assertThat(author.getBio()).isEqualTo(DARROW_BIO);
                });
        }

        @Test
        void shouldFillQidOnAuthorMatchedByName() {
            final Author existing = new Author();
            existing.setName(DARROW_NAME);
            authorRepository.saveAndFlush(existing);

            bookUpsertService.upsertFromSource(redRising(List.of()));

            assertThat(authorRepository.count())
                .as("the name lookup reuses the existing row")
                .isEqualTo(1L);
            assertThat(authorRepository.findByWikidataQid(DARROW_QID))
                .get()
                .extracting(Author::getPhotoUrl)
                .isEqualTo(DARROW_PHOTO);
        }

        @Test
        void shouldReuseAuthorMatchedByQidUnderAnotherName() {
            final Author existing = new Author();
            existing.setName(REAPER_NAME);
            existing.setWikidataQid(DARROW_QID);
            authorRepository.saveAndFlush(existing);

            bookUpsertService.upsertFromSource(redRising(List.of()));

            assertThat(authorRepository.count()).isEqualTo(1L);
            assertThat(storedAuthorNames()).containsExactly(REAPER_NAME);
        }

        @Test
        void shouldApplyVerifiedAuthors() {
            final long bookId = bookUpsertService.upsertFromSource(redRising(List.of())).getBookId();

            bookUpsertService.applyVerified(bookId, verifiedAuthor(REAPER_NAME));

            assertThat(storedAuthorNames()).containsExactly(REAPER_NAME);
        }

        @Test
        void shouldKeepVerifiedAuthorsOnRefresh() {
            final long bookId = bookUpsertService.upsertFromSource(redRising(List.of())).getBookId();
            bookUpsertService.applyVerified(bookId, verifiedAuthor(REAPER_NAME));

            bookUpsertService.upsertFromSource(redRising(List.of()));

            assertThat(storedAuthorNames()).containsExactly(REAPER_NAME);
        }

        private static VerifiedMetadata verifiedAuthor(final String name) {
            return new VerifiedMetadata(null, List.of(name), null, null, null, null, null);
        }

        @Test
        void shouldKeepStoredAuthorsWhenSourceHasNone() {
            bookUpsertService.upsertFromSource(redRising(List.of()));

            bookUpsertService.upsertFromSource(redRisingBy(null));

            assertThat(storedAuthorNames()).containsExactly(DARROW_NAME);
        }

        @Test
        void shouldKeepStoredAuthorsWhenEveryNameIsBlank() {
            bookUpsertService.upsertFromSource(redRising(List.of()));

            bookUpsertService.upsertFromSource(redRisingBy(SourceAuthor.ofNames(List.of(" "))));

            assertThat(storedAuthorNames()).containsExactly(DARROW_NAME);
        }

        private List<String> storedAuthorNames() {
            final Book book = bookRepository.findByWikidataQid(RED_RISING_QID).orElseThrow();
            return book.getAuthors().stream().map(Author::getName).toList();
        }
    }
}
