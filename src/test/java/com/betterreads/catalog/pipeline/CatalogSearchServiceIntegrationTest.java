package com.betterreads.catalog.pipeline;

import com.betterreads.support.ContainerizedTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.betterreads.catalog.entity.PendingBook;
import com.betterreads.catalog.repository.PendingBookRepository;
import com.betterreads.catalog.service.source.BookFieldSource;
import com.betterreads.catalog.service.pipeline.CatalogSearchService;
import com.betterreads.catalog.service.source.SourceAuthor;
import com.betterreads.catalog.service.source.SourceBook;
import com.betterreads.catalog.service.source.SourceAuthorWorks;
import com.betterreads.catalog.service.source.SourceSeries;
import com.betterreads.catalog.service.source.SourceSeriesVolume;
import com.betterreads.integration.googlebooks.GoogleBooksClient;
import com.betterreads.integration.hardcover.HardcoverAuthorClient;
import com.betterreads.integration.hardcover.HardcoverClient;
import com.betterreads.integration.hardcover.HardcoverSeriesClient;
import com.betterreads.integration.loc.LocClient;
import com.betterreads.integration.openlibrary.OpenLibraryClient;
import com.betterreads.integration.wikidata.WikidataClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that a catalog search stages one pending candidate per series volume against a real
 * Postgres. The series spine resolves the volumes; OpenLibrary fills each by title and author so it
 * stages under its own work key. The other source clients are mocked to empty so the test stays off
 * the network and the staged key is deterministic.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "jwt.secret=integration-test-secret-must-be-at-least-256-bits-long-padding-padding",
    "jwt.issuer=betterreads-it",
    "betterreads.catalog.staging.poll-enabled=false"
})
class CatalogSearchServiceIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String SERIES_QUERY = "The Wheel of Time";

    private static final String AUTHOR = "Robert Jordan";

    private static final String SANDERSON = "Brandon Sanderson";

    private static final String NO_MATCH = "a title hardcover has no series for";

    private static final String STANDALONE_QUERY = "nineteen eighty-four orwell";

    private static final String CANONICAL_TITLE = "Nineteen Eighty-Four";

    private static final String CANONICAL_KEY = "OL1168083W";

    private static final int CANONICAL_YEAR = 1949;

    private static final int LATER_EDITION_YEAR = 2003;

    private static final int FALLBACK_SEARCH_LIMIT = 5;

    private static final int FIRST_POSITION = 1;

    private static final int SECOND_POSITION = 2;

    private static final int THIRD_POSITION = 3;

    private static final long VOLUME_COUNT = 3L;

    private static final long AUTHOR_BOOK_COUNT = 2L;

    private static final String SECOND_VOLUME_KEY = "OL2W";

    private static final String EYE = "The Eye of the World";

    private static final String GREAT_HUNT = "The Great Hunt";

    private static final String DRAGON_REBORN = "The Dragon Reborn";

    private static final String MISTBORN = "Mistborn: The Final Empire";

    private static final String WAY_OF_KINGS = "The Way of Kings";

    private static final Map<String, String> WORK_KEYS_BY_TITLE = Map.of(
        EYE, "OL1W",
        GREAT_HUNT, SECOND_VOLUME_KEY,
        DRAGON_REBORN, "OL3W",
        MISTBORN, "OL10W",
        WAY_OF_KINGS, "OL11W");

    @MockitoBean
    private HardcoverSeriesClient seriesClient;

    @MockitoBean
    private HardcoverAuthorClient authorClient;

    @MockitoBean
    private OpenLibraryClient openLibraryClient;

    @MockitoBean
    private HardcoverClient hardcoverClient;

    @MockitoBean
    private GoogleBooksClient googleBooksClient;

    @MockitoBean
    private WikidataClient wikidataClient;

    @MockitoBean
    private LocClient locClient;

    @Autowired
    private CatalogSearchService searchService;

    @Autowired
    private PendingBookRepository pendingBooks;

    @BeforeEach
    void stubSources() {
        pendingBooks.deleteAll();
        when(seriesClient.fetchSeries(SERIES_QUERY)).thenReturn(Optional.of(wheelOfTime()));
        when(seriesClient.fetchSeries(NO_MATCH)).thenReturn(Optional.empty());
        when(authorClient.fetchAuthorWorks(SANDERSON)).thenReturn(Optional.of(sandersonWorks()));
        when(openLibraryClient.source()).thenReturn(BookFieldSource.OPEN_LIBRARY);
        when(openLibraryClient.fetchByTitleAuthor(anyString(), anyString()))
            .thenAnswer(invocation -> openLibraryHit(invocation.getArgument(0)));
        when(openLibraryClient.search(anyString(), anyInt())).thenReturn(List.of());
        when(hardcoverClient.source()).thenReturn(BookFieldSource.HARDCOVER);
        when(hardcoverClient.fetchByTitleAuthor(anyString(), anyString())).thenReturn(Optional.empty());
        when(googleBooksClient.source()).thenReturn(BookFieldSource.GOOGLE_BOOKS);
        when(googleBooksClient.fetchByTitleAuthor(anyString(), anyString())).thenReturn(Optional.empty());
        when(wikidataClient.source()).thenReturn(BookFieldSource.WIKIDATA);
        when(wikidataClient.fetchByTitleAuthor(anyString(), anyString())).thenReturn(Optional.empty());
        when(locClient.source()).thenReturn(BookFieldSource.LOC);
        when(locClient.fetchByTitleAuthor(anyString(), anyString())).thenReturn(Optional.empty());
    }

    private static SourceSeries wheelOfTime() {
        return new SourceSeries(SERIES_QUERY, AUTHOR, List.of(
            new SourceSeriesVolume(FIRST_POSITION, volume(EYE, FIRST_POSITION)),
            new SourceSeriesVolume(SECOND_POSITION, volume(GREAT_HUNT, SECOND_POSITION)),
            new SourceSeriesVolume(THIRD_POSITION, volume(DRAGON_REBORN, THIRD_POSITION))));
    }

    private static SourceAuthorWorks sandersonWorks() {
        return new SourceAuthorWorks(SANDERSON, List.of(
            authorBook(MISTBORN), authorBook(WAY_OF_KINGS)));
    }

    private static SourceBook volume(final String title, final int position) {
        return SourceBook.builder(BookFieldSource.HARDCOVER)
            .title(title)
            .authors(SourceAuthor.ofNames(List.of(AUTHOR)))
            .seriesName(SERIES_QUERY)
            .seriesPosition(position)
            .build();
    }

    private static SourceBook authorBook(final String title) {
        return SourceBook.builder(BookFieldSource.HARDCOVER)
            .title(title)
            .authors(SourceAuthor.ofNames(List.of(SANDERSON)))
            .build();
    }

    private static List<SourceBook> noisyStandaloneHits() {
        return List.of(
            standaloneHit("SparkNotes for 1984", "OLsparkW", LATER_EDITION_YEAR),
            standaloneHit("1984 (adaptation)", "OLadaptW", LATER_EDITION_YEAR),
            standaloneHit("Animal Farm / Nineteen Eighty-Four", "OLcomboW", LATER_EDITION_YEAR),
            standaloneHit(CANONICAL_TITLE, "OLreprintW", LATER_EDITION_YEAR),
            standaloneHit(CANONICAL_TITLE, CANONICAL_KEY, CANONICAL_YEAR));
    }

    private static SourceBook standaloneHit(final String title, final String key, final int year) {
        return SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
            .openLibraryWorkKey(key)
            .title(title)
            .publicationYear(year)
            .authors(SourceAuthor.ofNames(List.of("George Orwell")))
            .build();
    }

    private static Optional<SourceBook> openLibraryHit(final String title) {
        final String workKey = WORK_KEYS_BY_TITLE.get(title);
        if (workKey == null) {
            return Optional.empty();
        }
        return Optional.of(SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
            .openLibraryWorkKey(workKey)
            .title(title)
            .authors(SourceAuthor.ofNames(List.of(AUTHOR)))
            .build());
    }

    @Test
    @DisplayName("a series query stages one candidate per volume under its own work key")
    void seriesQueryStagesOneCandidatePerVolume() {
        searchService.searchAndStage(SERIES_QUERY);

        assertThat(pendingBooks.count())
            .as("each of the three volumes stages as its own candidate")
            .isEqualTo(VOLUME_COUNT);
        assertThat(pendingBooks.findByOpenLibraryWorkKey(SECOND_VOLUME_KEY))
            .as("a middle volume stages under its own key, carrying its series position")
            .get()
            .extracting(PendingBook::getSeriesPosition)
            .isEqualTo(SECOND_POSITION);
    }

    @Test
    @DisplayName("a query with no series match and no single-book hit stages nothing")
    void noSeriesMatchStagesNothing() {
        searchService.searchAndStage(NO_MATCH);

        assertThat(pendingBooks.count())
            .as("with no series and no fallback hit, nothing stages")
            .isZero();
    }

    @Test
    @DisplayName("an author query stages one candidate per book of the matching author")
    void authorQueryStagesOneCandidatePerBook() {
        searchService.searchAuthorAndStage(SANDERSON);

        assertThat(pendingBooks.count())
            .as("each of the author's books stages as its own candidate")
            .isEqualTo(AUTHOR_BOOK_COUNT);
    }

    @Test
    @DisplayName("a free-form query longer than the title still stages the canonical work")
    void standaloneFallbackStagesCanonicalWork() {
        when(openLibraryClient.search(STANDALONE_QUERY, FALLBACK_SEARCH_LIMIT))
            .thenReturn(noisyStandaloneHits());

        searchService.searchAndStage(STANDALONE_QUERY);

        assertThat(pendingBooks.findAll())
            .as("the study guide, adaptation, combo, and 2021 reprint are rejected, leaving the 1949 work")
            .singleElement()
            .satisfies(staged -> {
                assertThat(staged.getOpenLibraryWorkKey()).isEqualTo(CANONICAL_KEY);
                assertThat(staged.getFirstPublishYear()).isEqualTo(CANONICAL_YEAR);
            });
    }
}
