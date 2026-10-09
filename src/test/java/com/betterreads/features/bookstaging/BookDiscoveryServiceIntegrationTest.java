package com.betterreads.features.bookstaging;

import com.betterreads.booksource.BookFieldSource;
import com.betterreads.clients.googlebooks.GoogleBooksClient;
import com.betterreads.clients.hardcoverauthor.HardcoverAuthorClient;
import com.betterreads.clients.hardcoverbook.HardcoverClient;
import com.betterreads.clients.hardcoverseries.HardcoverSeriesClient;
import com.betterreads.clients.loc.LocClient;
import com.betterreads.clients.openlibrary.OpenLibraryClient;
import com.betterreads.clients.wikidata.WikidataClient;
import com.betterreads.pendingbook.PendingBook;
import com.betterreads.pendingbook.PendingBookRepository;
import com.betterreads.testsupport.ContainerizedTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "betterreads.catalog.staging.poll-enabled=false"
})
class BookDiscoveryServiceIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String SERIES_QUERY = DiscoverySamples.WHEEL_OF_TIME;

    private static final String GAPPED_SERIES_QUERY = DiscoverySamples.GAPPED_WHEEL_OF_TIME;

    private static final String TITLE_BOOK_QUERY = "The Wheel of Time, with its title book";

    private static final String AUTHOR_QUERY = DiscoverySamples.SANDERSON;

    private static final String NO_MATCH = "a title hardcover has no series for";

    private static final String STANDALONE_QUERY = "nineteen eighty-four orwell";

    private static final int FALLBACK_SEARCH_LIMIT = 5;

    private static final long VOLUME_COUNT = 3L;

    private static final long AUTHOR_BOOK_COUNT = 2L;

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
    private BookDiscoveryService bookDiscovery;

    @Autowired
    private PendingBookRepository pendingBooks;

    @BeforeEach
    void stubSources() {
        pendingBooks.deleteAll();
        when(seriesClient.fetchSeries(SERIES_QUERY)).thenReturn(Optional.of(DiscoverySamples.wheelOfTime()));
        when(seriesClient.fetchSeries(GAPPED_SERIES_QUERY))
            .thenReturn(Optional.of(DiscoverySamples.gappedWheelOfTime()));
        when(seriesClient.fetchSeries(TITLE_BOOK_QUERY))
            .thenReturn(Optional.of(DiscoverySamples.titledWheelOfTime()));
        when(seriesClient.fetchSeries(NO_MATCH)).thenReturn(Optional.empty());
        when(authorClient.fetchAuthorWorks(AUTHOR_QUERY)).thenReturn(Optional.of(DiscoverySamples.sandersonWorks()));
        when(openLibraryClient.source()).thenReturn(BookFieldSource.OPEN_LIBRARY);
        when(openLibraryClient.fetchByTitleAuthor(anyString(), anyString()))
            .thenAnswer(invocation -> DiscoverySamples.openLibraryHit(invocation.getArgument(0)));
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

    @Test
    @DisplayName("a series query stages one candidate per volume under its own work key")
    void seriesQueryStagesOneCandidatePerVolume() {
        bookDiscovery.searchAndStage(SERIES_QUERY);

        assertThat(pendingBooks.count())
            .as("each of the three volumes stages as its own candidate")
            .isEqualTo(VOLUME_COUNT);
        assertThat(pendingBooks.findByOpenLibraryWorkKey(DiscoverySamples.SECOND_VOLUME_KEY))
            .as("a middle volume stages under its own key, carrying its series position")
            .get()
            .extracting(PendingBook::getSeriesPosition)
            .isEqualTo((double) DiscoverySamples.SECOND_POSITION);
    }

    @Test
    @DisplayName("a series with a volume no source identifies still stages the other volumes")
    void shouldStageOtherVolumesWhenOneHasNoIdentifier() {
        bookDiscovery.searchAndStage(GAPPED_SERIES_QUERY);

        assertThat(pendingBooks.findAll())
            .extracting(PendingBook::getTitle)
            .containsExactlyInAnyOrder(DiscoverySamples.EYE, DiscoverySamples.DRAGON_REBORN);
    }

    @Test
    void shouldStageTheTitleBookWithTheVolumes() {
        bookDiscovery.searchAndStage(TITLE_BOOK_QUERY);

        assertThat(pendingBooks.findAll())
            .extracting(PendingBook::getTitle)
            .containsExactlyInAnyOrder(DiscoverySamples.EYE, DiscoverySamples.GREAT_HUNT,
                DiscoverySamples.DRAGON_REBORN, DiscoverySamples.WHEEL_OF_TIME);
    }

    @Test
    @DisplayName("a query whose OpenLibrary hits do not match the title stages nothing")
    void noSeriesMatchStagesNothing() {
        when(openLibraryClient.search(NO_MATCH, FALLBACK_SEARCH_LIMIT))
            .thenReturn(DiscoverySamples.noisyStandaloneHits());

        bookDiscovery.searchAndStage(NO_MATCH);

        assertThat(pendingBooks.count())
            .as("no hit's title sits within the query, so nothing stages")
            .isZero();
    }

    @Test
    @DisplayName("an author query stages one candidate per book of the matching author")
    void authorQueryStagesOneCandidatePerBook() {
        bookDiscovery.searchAuthorAndStage(AUTHOR_QUERY);

        assertThat(pendingBooks.count())
            .as("each of the author's books stages as its own candidate")
            .isEqualTo(AUTHOR_BOOK_COUNT);
    }

    @Test
    @DisplayName("a search that matches an author stages the author's books and skips the title search")
    void shouldStageAuthorBooksWithoutTitleSearch() {
        bookDiscovery.searchAndStage(AUTHOR_QUERY);

        assertThat(pendingBooks.count()).isEqualTo(AUTHOR_BOOK_COUNT);
        verify(openLibraryClient, never()).search(anyString(), anyInt());
    }

    @Test
    @DisplayName("a free-form query longer than the title still stages the canonical work")
    void standaloneFallbackStagesCanonicalWork() {
        when(openLibraryClient.search(STANDALONE_QUERY, FALLBACK_SEARCH_LIMIT))
            .thenReturn(DiscoverySamples.noisyStandaloneHits());

        bookDiscovery.searchAndStage(STANDALONE_QUERY);

        assertThat(pendingBooks.findAll())
            .as("the study guide, adaptation, and combo are filtered out and the reprint loses on year")
            .singleElement()
            .satisfies(staged -> {
                assertThat(staged.getOpenLibraryWorkKey()).isEqualTo(DiscoverySamples.CANONICAL_KEY);
                assertThat(staged.getFirstPublishYear()).isEqualTo(DiscoverySamples.CANONICAL_YEAR);
            });
    }
}
