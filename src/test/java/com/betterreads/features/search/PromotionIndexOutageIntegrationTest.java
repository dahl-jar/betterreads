package com.betterreads.features.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.betterreads.book.BookRepository;
import com.betterreads.bookmerge.SourceMerger;
import com.betterreads.booksource.SourceBook;
import com.betterreads.booksource.SourceBooks;
import com.betterreads.features.bookstaging.PendingBookService;
import com.betterreads.pendingbook.PendingBookRepository;
import com.betterreads.testsupport.ContainerizedTest;
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

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "betterreads.catalog.staging.poll-enabled=false",
    "meilisearch.host=http://localhost:1",
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
        pendingBookService.stage(merger.merge(null, List.of(dune)));

        assertThatCode(pendingBookService::promoteReady).doesNotThrowAnyException();

        assertThat(books.findByDedupKey(dune.isbn13()))
            .as("the book is promoted even though indexing it failed")
            .isPresent();
    }

    @TestConfiguration
    static class FailingSearch {

        @Bean
        @Primary
        BookSearchService failingSearchService() {
            return new OutageSearchService();
        }
    }

    /** index throws like Meilisearch does when it is down, search returns nothing */
    private static final class OutageSearchService implements BookSearchService {

        @Override
        public SearchOutcome search(final String query, final int offset, final int limit) {
            return new SearchOutcome(new BookSearchResult(List.of(), 0, offset, limit), false);
        }

        @Override
        public Optional<BookSearchDocument> hitFor(final String query, final String bookId) {
            return Optional.empty();
        }

        @Override
        public void index(final Collection<BookSearchDocument> documents) {
            throw new SearchIndexException("simulated Meilisearch outage", new IllegalStateException());
        }
    }
}
