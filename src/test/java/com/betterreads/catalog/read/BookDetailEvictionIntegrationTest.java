package com.betterreads.catalog.read;

import static org.assertj.core.api.Assertions.assertThat;

import com.betterreads.catalog.dto.BookDetailResponse;
import com.betterreads.catalog.service.source.BookFieldSource;
import com.betterreads.catalog.service.read.BookReadService;
import com.betterreads.catalog.service.read.CatalogService;
import com.betterreads.catalog.service.source.SourceAuthor;
import com.betterreads.catalog.service.source.SourceBook;
import com.betterreads.catalog.repository.AuthorRepository;
import com.betterreads.catalog.repository.BookRepository;
import com.betterreads.support.ContainerizedTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Reading a book detail then re-writing the same book through the catalog serves the new data, so a
 * refreshed book is never stuck behind a stale Redis entry.
 */
@SpringBootTest
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
    "jwt.secret=integration-test-secret-must-be-at-least-256-bits-long-padding-padding",
    "jwt.issuer=betterreads-it"
})
class BookDetailEvictionIntegrationTest extends ContainerizedTest {

    private static final String ISBN = "9780553103540";

    private static final String ORIGINAL_TITLE = "A Game of Thrones";

    private static final String REVISED_TITLE = "A Game of Thrones (Revised)";

    private static final int PUBLISH_YEAR = 1996;

    private static final int EVICTION_READ_ATTEMPTS = 50;

    private static final long EVICTION_READ_INTERVAL_MILLIS = 100L;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private BookReadService bookReadService;

    @Autowired
    private BookRepository books;

    @Autowired
    private AuthorRepository authors;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void clearCatalog() {
        books.deleteAll();
        authors.deleteAll();
        cacheManager.getCache("bookDetails").clear();
    }

    @Test
    @DisplayName("re-writing a book serves the new title, not the cached one")
    void evictsOnReWrite() {
        catalogService.upsertFromSource(book(ORIGINAL_TITLE));
        final BookDetailResponse cached = bookReadService.findByKey(ISBN).orElseThrow();

        catalogService.upsertFromSource(book(REVISED_TITLE));

        assertThat(cached.title()).isEqualTo(ORIGINAL_TITLE);
        assertThat(titleAfterEviction())
            .as("the re-write evicts the cached entry, so the next read serves the new title")
            .isEqualTo(REVISED_TITLE);
    }

    /**
     * Reads the title, retrying briefly because the shared Redis cache makes eviction visible
     * asynchronously under load. The read settles within a bound; a missing eviction never settles
     * and still fails.
     */
    // PMD.DoNotUseThreads: a bounded test poll for an eventually-visible cache eviction, not app code.
    @SuppressWarnings("PMD.DoNotUseThreads")
    private String titleAfterEviction() {
        String title = "";
        for (int attempt = 0; attempt < EVICTION_READ_ATTEMPTS; attempt++) {
            title = bookReadService.findByKey(ISBN).orElseThrow().title();
            if (REVISED_TITLE.equals(title)) {
                return title;
            }
            try {
                Thread.sleep(EVICTION_READ_INTERVAL_MILLIS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return title;
            }
        }
        return title;
    }

    private static SourceBook book(final String title) {
        return SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
            .isbn13(ISBN)
            .openLibraryWorkKey("OL_AGOT")
            .title(title)
            .description("Noble families vie for the Iron Throne in Westeros.")
            .coverUrl("https://covers.example/agot.jpg")
            .publicationYear(PUBLISH_YEAR)
            .authors(List.of(SourceAuthor.ofName("George R. R. Martin")))
            .build();
    }
}
