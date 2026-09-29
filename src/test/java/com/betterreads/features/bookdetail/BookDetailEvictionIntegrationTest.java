package com.betterreads.features.bookdetail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.betterreads.book.AuthorRepository;
import com.betterreads.book.BookDetailCache;
import com.betterreads.book.BookRepository;
import com.betterreads.book.BookUpsertService;
import com.betterreads.book.VerifiedMetadata;
import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.SourceAuthor;
import com.betterreads.booksource.SourceBook;
import com.betterreads.testsupport.ContainerizedTest;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cache.CacheManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Re-writing a cached book through the catalog evicts its Redis entry. */
@SpringBootTest
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BookDetailEvictionIntegrationTest extends ContainerizedTest {

    private static final String ISBN = "9780553103540";

    private static final String ORIGINAL_TITLE = "A Game of Thrones";

    private static final String REVISED_TITLE = "A Game of Thrones (Revised)";

    private static final int PUBLISH_YEAR = 1996;

    private static final Duration EVICTION_TIMEOUT = Duration.ofSeconds(5);

    private static final Duration EVICTION_POLL_INTERVAL = Duration.ofMillis(100);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    @Autowired
    private BookUpsertService bookUpsertService;

    @Autowired
    private BookDetailService bookDetailService;

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
        cacheManager.getCache(BookDetailCache.NAME).clear();
    }

    @Test
    @DisplayName("re-writing a book serves the new title")
    void evictsOnReWrite() {
        bookUpsertService.upsertFromSource(book(ORIGINAL_TITLE));
        bookDetailService.findByKey(ISBN);

        bookUpsertService.upsertFromSource(book(REVISED_TITLE));

        await().atMost(EVICTION_TIMEOUT).pollInterval(EVICTION_POLL_INTERVAL).untilAsserted(() -> {
            final BookDetailResponse fresh = bookDetailService.findByKey(ISBN).orElseThrow();
            assertThat(fresh.title()).isEqualTo(REVISED_TITLE);
        });
    }

    @Test
    void shouldServeVerifiedTitle() {
        final long bookId = bookUpsertService.upsertFromSource(book(ORIGINAL_TITLE)).getBookId();
        bookDetailService.findByKey(ISBN);

        final VerifiedMetadata verified = new VerifiedMetadata(REVISED_TITLE, null, null, null, null, null, null);
        bookUpsertService.applyVerified(bookId, verified);

        await().atMost(EVICTION_TIMEOUT).pollInterval(EVICTION_POLL_INTERVAL).untilAsserted(() -> {
            final BookDetailResponse fresh = bookDetailService.findByKey(ISBN).orElseThrow();
            assertThat(fresh.title()).isEqualTo(REVISED_TITLE);
        });
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
