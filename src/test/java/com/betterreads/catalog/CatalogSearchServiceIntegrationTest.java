package com.betterreads.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import com.betterreads.catalog.repository.PendingBookRepository;
import com.betterreads.catalog.service.BookFieldSource;
import com.betterreads.catalog.service.CatalogSearchService;
import com.betterreads.catalog.service.SourceAuthor;
import com.betterreads.catalog.service.SourceBook;
import com.betterreads.integration.openlibrary.OpenLibraryClient;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifies that a catalog search stages one pending candidate per matching book against a real
 * Postgres. A series query stages every volume as its own candidate, keyed by its work key, so the
 * later poll can complete and promote each one.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "jwt.secret=integration-test-secret-must-be-at-least-256-bits-long-padding-padding",
    "jwt.issuer=betterreads-it",
    "betterreads.catalog.staging.poll-enabled=false"
})
@Import(CatalogSearchServiceIntegrationTest.StubSearch.class)
class CatalogSearchServiceIntegrationTest {

    private static final String AUTHOR = "Robert Jordan";

    private static final long EXPECTED_VOLUMES = 3L;

    private static final String MIDDLE_VOLUME_KEY = "OL2W";

    private static final String BOXED_SET_KEY = "OL99W";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17"));

    @Autowired
    private CatalogSearchService searchService;

    @Autowired
    private PendingBookRepository pendingBooks;

    @BeforeEach
    void clearPending() {
        pendingBooks.deleteAll();
    }

    @Test
    @DisplayName("a series query stages each single volume and skips the boxed set in the results")
    void seriesQueryStagesVolumesAndSkipsCollections() {
        searchService.searchAndStage("The Wheel of Time");

        assertThat(pendingBooks.count())
            .as("the three single novels stage; the boxed set in the search results does not")
            .isEqualTo(EXPECTED_VOLUMES);
        assertThat(pendingBooks.findByOpenLibraryWorkKey(MIDDLE_VOLUME_KEY))
            .as("a middle volume is staged under its own work key, not merged into the first")
            .isPresent();
        assertThat(pendingBooks.findByOpenLibraryWorkKey(BOXED_SET_KEY))
            .as("a boxed set is a collection, not a single book, so it is not staged")
            .isEmpty();
    }

    @TestConfiguration
    static class StubSearch {

        @Bean
        @Primary
        OpenLibraryClient stubOpenLibraryClient() {
            return new StubOpenLibraryClient(List.of(
                volume("OL1W", "The Eye of the World"),
                volume(MIDDLE_VOLUME_KEY, "The Great Hunt"),
                volume("OL3W", "The Dragon Reborn"),
                volume(BOXED_SET_KEY, "The Wheel of Time (Boxed Set #1)")));
        }

        private static SourceBook volume(final String workKey, final String title) {
            return SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
                .openLibraryWorkKey(workKey)
                .title(title)
                .authors(SourceAuthor.ofNames(List.of(AUTHOR)))
                .build();
        }
    }

    private static final class StubOpenLibraryClient implements OpenLibraryClient {

        private final List<SourceBook> hits;

        StubOpenLibraryClient(final List<SourceBook> hits) {
            this.hits = List.copyOf(hits);
        }

        @Override
        public List<SourceBook> search(final String query, final int limit) {
            return hits;
        }

        @Override
        public BookFieldSource source() {
            return BookFieldSource.OPEN_LIBRARY;
        }

        @Override
        public Optional<SourceBook> fetchByIsbn(final String isbn) {
            return Optional.empty();
        }

        @Override
        public Optional<SourceBook> fetchByTitleAuthor(final String title, final String author) {
            return Optional.empty();
        }

        @Override
        public Optional<SourceBook> fetchByWorkKey(final String workKey) {
            return Optional.empty();
        }
    }
}
