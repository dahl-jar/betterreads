package com.betterreads.catalog.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import com.betterreads.catalog.entity.Book;
import com.betterreads.catalog.repository.AuthorRepository;
import com.betterreads.catalog.repository.BookRepository;
import com.betterreads.catalog.service.read.CatalogService;
import com.betterreads.catalog.service.source.SourceBook;
import com.betterreads.common.util.LogSanitizer;
import com.betterreads.integration.openlibrary.OpenLibraryClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Seeds the locally-running compose Postgres with a 20-book slate fetched live from OpenLibrary,
 * so the catalog can be inspected with {@code psql} after the JVM exits. This is the clean-metadata
 * seed for manual review of the genre filter, original publication years, and cover URLs.
 *
 * <p>Skipped unless {@code RUN_LOCAL_DB_VERIFICATION=1}. Opt-in because it writes to a real
 * long-lived database and makes 20 sequential live calls. Run with:
 * <pre>
 *   docker compose up -d db
 *   RUN_LOCAL_DB_VERIFICATION=1 ./gradlew test --tests '*CatalogOpenLibrarySeedVerification*'
 *   docker exec -it betterreads-db psql -U betterreads -d betterreads -c \
 *     "SELECT b.title, b.first_publish_year, string_agg(s.subject, ', ') \
 *      FROM book b LEFT JOIN book_subject s USING (book_id) GROUP BY b.book_id ORDER BY b.title;"
 * </pre>
 *
 * <p>A short delay between fetches keeps the sequence polite against the keyless OpenLibrary API.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "openlibrary.base-url=https://openlibrary.org",
    "openlibrary.contact-email=seed@betterreadsapp.com",
    "openlibrary.connect-timeout=5000",
    "openlibrary.read-timeout=15000",
    "jwt.secret=local-seed-secret-must-be-at-least-256-bits-long-padding-padding-padding",
    "jwt.issuer=betterreads-seed",
    "jwt.expiration-minutes=60",
    "jwt.refresh-expiration-days=30",
    "mail.outbox.worker-enabled=false"
})
@EnabledIfEnvironmentVariable(named = "RUN_LOCAL_DB_VERIFICATION", matches = "1")
// PMD.ClassNamingConventions: not *Test on purpose, an opt-in manual DB seed, not a CI test
// PMD.DoNotUseThreads: the sleep is a polite delay between keyless OpenLibrary calls, not webapp threading
@SuppressWarnings({"PMD.ClassNamingConventions", "PMD.DoNotUseThreads"})
class CatalogOpenLibrarySeedVerification {

    private static final Logger LOG = LoggerFactory.getLogger(CatalogOpenLibrarySeedVerification.class);

    private static final int EXPECTED_SEED_SIZE = 20;

    private static final long ALLOWED_MISSES = 3;

    private static final long POLITE_DELAY_MS = 400;

    private static final List<TitleAuthor> SLATE = List.of(
        new TitleAuthor("The Hobbit", "J.R.R. Tolkien"),
        new TitleAuthor("The Eye of the World", "Robert Jordan"),
        new TitleAuthor("Dune", "Frank Herbert"),
        new TitleAuthor("A Clash of Kings", "George R. R. Martin"),
        new TitleAuthor("The Name of the Wind", "Patrick Rothfuss"),
        new TitleAuthor("Mistborn", "Brandon Sanderson"),
        new TitleAuthor("Nineteen Eighty-Four", "George Orwell"),
        new TitleAuthor("Brave New World", "Aldous Huxley"),
        new TitleAuthor("Fahrenheit 451", "Ray Bradbury"),
        new TitleAuthor("Pride and Prejudice", "Jane Austen"),
        new TitleAuthor("Crime and Punishment", "Fyodor Dostoevsky"),
        new TitleAuthor("The Great Gatsby", "F. Scott Fitzgerald"),
        new TitleAuthor("To Kill a Mockingbird", "Harper Lee"),
        new TitleAuthor("The Hunger Games", "Suzanne Collins"),
        new TitleAuthor("The Martian", "Andy Weir"),
        new TitleAuthor("Neuromancer", "William Gibson"),
        new TitleAuthor("Watchmen", "Alan Moore"),
        new TitleAuthor("The Sandman", "Neil Gaiman"),
        new TitleAuthor("Sapiens", "Yuval Noah Harari"),
        new TitleAuthor("Educated", "Tara Westover"));

    @Autowired
    private OpenLibraryClient openLibraryClient;

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private AuthorRepository authorRepository;

    @Test
    @DisplayName("seeds 20 books from OpenLibrary into the local compose Postgres with clean metadata")
    void seedsTwentyBooks() throws InterruptedException {
        bookRepository.deleteAll();
        authorRepository.deleteAll();

        int persisted = 0;
        for (final TitleAuthor entry : SLATE) {
            final Optional<SourceBook> source =
                openLibraryClient.fetchByTitleAuthor(entry.title(), entry.author());
            if (source.isEmpty()) {
                LOG.warn("catalog.seed.miss OpenLibrary returned nothing title={}",
                    LogSanitizer.forLog(entry.title()));
            } else {
                final Book book = catalogService.upsertFromSource(source.get());
                persisted++;
                LOG.info("catalog.seed.persisted year={} subjects={}",
                    book.getFirstPublishYear(), book.getSubjects().size());
            }
            Thread.sleep(POLITE_DELAY_MS);
        }

        LOG.info("catalog.seed.done persisted={}/{}", persisted, SLATE.size());
        assertThat(bookRepository.count())
            .as("most of the slate should persist; OpenLibrary occasionally misses a title")
            .isGreaterThanOrEqualTo(EXPECTED_SEED_SIZE - ALLOWED_MISSES);
    }

    private record TitleAuthor(String title, String author) {
    }
}
