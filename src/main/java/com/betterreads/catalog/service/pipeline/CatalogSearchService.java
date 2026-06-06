package com.betterreads.catalog.service.pipeline;

import com.betterreads.catalog.service.source.MergedBook;
import com.betterreads.catalog.service.source.SingleBookFilter;
import com.betterreads.catalog.service.source.SourceBook;
import com.betterreads.catalog.service.source.SourceSeries;
import com.betterreads.catalog.service.source.SourceSeriesVolume;
import com.betterreads.common.util.TextMatch;

import java.util.Comparator;
import java.util.Optional;

import com.betterreads.integration.hardcover.HardcoverAuthorClient;
import com.betterreads.integration.hardcover.HardcoverSeriesClient;
import com.betterreads.integration.openlibrary.OpenLibraryClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * Turns a user search into staged candidates.
 *
 * <p>A series query resolves to a Hardcover series and stages one candidate per volume; an author
 * query resolves to a Hardcover author and stages one per book. Both fill each book across the other
 * sources by title and author. A series query with no match falls back to a single OpenLibrary hit,
 * so a standalone book still stages.
 */
@Service
public class CatalogSearchService {

    private static final Logger LOG = LoggerFactory.getLogger(CatalogSearchService.class);

    private static final int FALLBACK_SEARCH_LIMIT = 5;

    private final HardcoverSeriesClient seriesClient;

    private final HardcoverAuthorClient authorClient;

    private final OpenLibraryClient openLibraryClient;

    private final SourceCollector sourceCollector;

    private final PendingBookService pendingBookService;

    public CatalogSearchService(
        final HardcoverSeriesClient seriesClient,
        final HardcoverAuthorClient authorClient,
        final OpenLibraryClient openLibraryClient,
        final SourceCollector sourceCollector,
        final PendingBookService pendingBookService
    ) {
        this.seriesClient = seriesClient;
        this.authorClient = authorClient;
        this.openLibraryClient = openLibraryClient;
        this.sourceCollector = sourceCollector;
        this.pendingBookService = pendingBookService;
    }

    /**
     * Resolves a miss to a series, an author, or a standalone book and stages what it finds.
     *
     * <p>A series query stages each volume; an author query stages each of the author's books; a
     * title query stages the one standalone hit. The order goes from most specific to least so a
     * series or author name is expanded rather than collapsed to a single title fallback.
     */
    public void searchAndStage(final String query) {
        final Optional<SourceSeries> series = seriesClient.fetchSeries(query);
        if (series.isPresent()) {
            stageSeries(series.get());
            return;
        }
        if (stageAuthor(query)) {
            return;
        }
        stageStandalone(query);
    }

    /** Stages a candidate for each book of the matching author. */
    public void searchAuthorAndStage(final String query) {
        stageAuthor(query);
    }

    private boolean stageAuthor(final String query) {
        return authorClient.fetchAuthorWorks(query)
            .map(works -> {
                works.books().forEach(this::stage);
                return true;
            })
            .orElse(false);
    }

    private void stageSeries(final SourceSeries series) {
        for (final SourceSeriesVolume volume : series.volumes()) {
            stage(volume.book());
        }
    }

    private void stageStandalone(final String query) {
        openLibraryClient.search(query, FALLBACK_SEARCH_LIMIT).stream()
            .filter(hit -> hit.title() != null
                && TextMatch.titleWithinQuery(hit.title(), query)
                && SingleBookFilter.isSingleBook(hit.title()))
            .min(Comparator.comparing(CatalogSearchService::publishYearOrMax))
            .ifPresent(this::stage);
    }

    private static int publishYearOrMax(final SourceBook hit) {
        return hit.publicationYear() == null ? Integer.MAX_VALUE : hit.publicationYear();
    }

    private void stage(final SourceBook seed) {
        try {
            final MergedBook merged = sourceCollector.collectFor(seed);
            final String dedupKey = merged.book().dedupKey();
            if (dedupKey != null) {
                pendingBookService.stage(merged);
                pendingBookService.promoteNow(dedupKey, merged);
            }
        } catch (DataAccessException ex) {
            LOG.warn("catalog.search staging failed for source {} ({}), skipping it",
                seed.source(), ex.getClass().getSimpleName());
        }
    }
}
