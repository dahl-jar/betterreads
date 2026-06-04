package com.betterreads.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.betterreads.catalog.dto.BookDetailResponse;
import com.betterreads.catalog.service.BookFieldSource;
import com.betterreads.catalog.service.BookReadService;
import com.betterreads.catalog.service.CatalogService;
import com.betterreads.catalog.service.SourceAuthor;
import com.betterreads.catalog.service.SourceBook;
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
        final BookDetailResponse afterRewrite = bookReadService.findByKey(ISBN).orElseThrow();

        assertThat(cached.title()).isEqualTo(ORIGINAL_TITLE);
        assertThat(afterRewrite.title()).isEqualTo(REVISED_TITLE);
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
