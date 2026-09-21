package com.betterreads.catalog.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.betterreads.catalog.repository.BookRepository;
import com.betterreads.catalog.repository.PendingBookRepository;
import com.betterreads.catalog.service.pipeline.PendingBookService;
import com.betterreads.catalog.service.source.model.SourceBook;
import com.betterreads.catalog.service.source.model.SourceBooks;
import com.betterreads.catalog.service.source.merge.SourceMerger;
import com.betterreads.search.dto.BookSearchDocument;
import com.betterreads.search.dto.BookSearchResult;
import com.betterreads.search.dto.SearchOutcome;
import com.betterreads.search.service.BookSearchService;
import com.betterreads.search.service.SearchIndexException;
import com.betterreads.support.ContainerizedTest;
import java.util.Collection;
import java.util.Optional;
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
 * stop the promotion loop.
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
@Import({NoNetworkSources.class, PromotionIndexOutageIntegrationTest.FailingSearch.class})
class PromotionIndexOutageIntegrationTest extends ContainerizedTest {

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
        final SourceBook dune = SourceBooks.dune();
        pendingBookService.stage(merger.merge(List.of(dune)));

        assertThatCode(pendingBookService::promoteReady).doesNotThrowAnyException();

        assertThat(books.findByDedupKey(dune.isbn13()))
            .as("the book is promoted even though indexing it failed")
            .isPresent();
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

    /** Throws on index to simulate the outage; search returns nothing and remove is never called. */
    private static final class OutageSearchService implements BookSearchService {

        @Override
        public BookSearchResult search(final String query, final int offset, final int limit) {
            return new BookSearchResult(List.of(), 0, offset, limit);
        }

        @Override
        public SearchOutcome searchOutcome(final String query, final int offset, final int limit) {
            return new SearchOutcome(search(query, offset, limit), false);
        }

        @Override
        public Optional<BookSearchDocument> hitFor(final String query, final String bookId) {
            return Optional.empty();
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
