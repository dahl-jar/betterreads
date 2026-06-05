package com.betterreads.catalog.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.betterreads.catalog.repository.BookRepository;
import com.betterreads.catalog.repository.PendingBookRepository;
import com.betterreads.catalog.service.source.BookFieldSource;
import com.betterreads.catalog.service.pipeline.PendingBookService;
import com.betterreads.catalog.service.pipeline.RequiredFieldsCheck;
import com.betterreads.catalog.service.source.SourceAuthor;
import com.betterreads.catalog.service.source.SourceBook;
import com.betterreads.catalog.service.pipeline.SourceCollector;
import com.betterreads.catalog.service.source.SourceMerger;
import com.betterreads.search.dto.BookSearchDocument;
import com.betterreads.search.dto.BookSearchResult;
import com.betterreads.search.service.BookSearchService;
import com.betterreads.search.service.SearchIndexException;
import com.betterreads.support.ContainerizedTest;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * A Meilisearch outage during the post-promotion index hook leaves the book promoted and does not
 * stop the promotion loop, so other pending books still promote and the nightly reconcile heals the
 * unindexed one.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "jwt.secret=integration-test-secret-must-be-at-least-256-bits-long-padding-padding",
    "jwt.issuer=betterreads-it",
    "betterreads.catalog.staging.poll-enabled=false",
    "meilisearch.host=http://localhost:7700",
    "meilisearch.master-key=unused",
    "meilisearch.index-name=unused"
})
@Import({
    PromotionIndexOutageIntegrationTest.NoNetworkSources.class,
    PromotionIndexOutageIntegrationTest.FailingSearch.class
})
class PromotionIndexOutageIntegrationTest extends ContainerizedTest {

    private static final String ISBN = "9780441013593";

    private static final int DUNE_YEAR = 1965;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    @Autowired
    private SourceMerger merger;

    @Autowired
    private PendingBookService pendingBookService;

    @Autowired
    private BookRepository books;

    @Autowired
    private PendingBookRepository pendingBooks;

    @BeforeEach
    void clearCatalog() {
        pendingBooks.deleteAll();
        books.deleteAll();
    }

    @Test
    @DisplayName("promotion survives a Meilisearch outage in the index hook")
    void promotionSurvivesIndexOutage() {
        pendingBookService.stage(merger.merge(List.of(completeDune())));

        assertThatCode(pendingBookService::promoteReady).doesNotThrowAnyException();

        assertThat(books.findByDedupKey(ISBN))
            .as("the book is promoted even though indexing it failed")
            .isPresent();
    }

    private static SourceBook completeDune() {
        return SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
            .isbn13(ISBN)
            .openLibraryWorkKey("OL893415W")
            .title("Dune")
            .description("Paul Atreides leads the Fremen against the Padishah Empire on Arrakis.")
            .coverUrl("https://covers.example/dune.jpg")
            .publicationYear(DUNE_YEAR)
            .authors(List.of(SourceAuthor.ofName("Frank Herbert")))
            .build();
    }

    /** Replaces the source collector with one that re-merges only the staged data, no network. */
    @TestConfiguration
    static class NoNetworkSources {

        @Bean
        @Primary
        SourceCollector noNetworkSourceCollector(final SourceMerger merger) {
            return new SourceCollector(merger, new RequiredFieldsCheck(), List.of());
        }
    }

    /** A search service whose index call always fails, standing in for a Meilisearch outage. */
    @TestConfiguration
    static class FailingSearch {

        @Bean
        @Primary
        BookSearchService failingSearchService() {
            return new OutageSearchService();
        }
    }

    /** Throws on index to simulate the outage; search and remove are inert. */
    private static final class OutageSearchService implements BookSearchService {

        @Override
        public BookSearchResult search(final String query, final int offset, final int limit) {
            return new BookSearchResult(List.of(), 0, offset, limit);
        }

        @Override
        public void index(final Collection<BookSearchDocument> documents) {
            throw new SearchIndexException("simulated Meilisearch outage", new IllegalStateException());
        }

        @Override
        public void remove(final String bookId) {
            throw new UnsupportedOperationException("remove is not exercised by the outage test");
        }
    }
}
