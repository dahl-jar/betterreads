package com.betterreads.features.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.betterreads.book.AuthorRepository;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BookChangedIndexIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final Duration INDEX_WAIT = Duration.ofSeconds(10);

    private static final int PAGE = 20;

    private static final int PUBLISH_YEAR = 1989;

    @Autowired
    private BookUpsertService bookUpsertService;

    @Autowired
    private BookSearchService searchService;

    @Autowired
    private BookRepository books;

    @Autowired
    private AuthorRepository authors;

    @DynamicPropertySource
    static void meilisearchProps(final DynamicPropertyRegistry registry) {
        MeilisearchServer.register(registry, "books-changed-test");
    }

    @BeforeEach
    void clearCatalog() {
        books.deleteAll();
        authors.deleteAll();
    }

    @Test
    void shouldReindexAfterAVerifiedChange() {
        final long bookId = bookUpsertService.upsertFromSource(hyperion()).getBookId();
        final VerifiedMetadata verified = new VerifiedMetadata("Endymion", null, null, null, null, null, null, null);

        bookUpsertService.applyVerified(bookId, verified, 1);

        await().atMost(INDEX_WAIT).untilAsserted(() -> {
            final SearchOutcome outcome = searchService.search("endymion", 0, PAGE);
            assertThat(outcome.result().hits()).hasSize(1);
        });
    }

    private static SourceBook hyperion() {
        return SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
            .isbn13("9780553283686")
            .openLibraryWorkKey("OL_HYPERION")
            .title("Hyperion")
            .description("Seven pilgrims travel to the Time Tombs on Hyperion.")
            .coverUrl("https://covers.example/hyperion.jpg")
            .publicationYear(PUBLISH_YEAR)
            .authors(List.of(SourceAuthor.ofName("Dan Simmons")))
            .build();
    }
}
