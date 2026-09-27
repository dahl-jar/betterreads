package com.betterreads.features.bookstaging;

import com.betterreads.bookdiscovery.BookDiscovery;
import com.betterreads.booksource.MergedBook;
import com.betterreads.booksource.SingleBookFilter;
import com.betterreads.booksource.SourceBook;
import com.betterreads.clients.hardcoverauthor.HardcoverAuthorClient;
import com.betterreads.clients.hardcoverseries.HardcoverSeriesClient;
import com.betterreads.clients.openlibrary.OpenLibraryClient;
import com.betterreads.text.TextMatch;

import java.util.Comparator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/** Turns a user search into staged candidates. */
@Service
class BookDiscoveryService implements BookDiscovery {

    private static final Logger LOG = LoggerFactory.getLogger(BookDiscoveryService.class);

    private static final int FALLBACK_SEARCH_LIMIT = 5;

    private final HardcoverSeriesClient seriesClient;

    private final HardcoverAuthorClient authorClient;

    private final OpenLibraryClient openLibraryClient;

    private final SourceCollector sourceCollector;

    private final PendingBookService pendingBookService;

    public BookDiscoveryService(
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

    /** Tries series, then author, then one OpenLibrary title hit, so a standalone book still stages. */
    @Override
    public void searchAndStage(final String query) {
        seriesClient.fetchSeries(query).ifPresentOrElse(
            series -> series.volumes().forEach(volume -> stage(volume.book())),
            () -> {
                if (!stageAuthor(query)) {
                    stageStandalone(query);
                }
            });
    }

    @Override
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

    private void stageStandalone(final String query) {
        openLibraryClient.search(query, FALLBACK_SEARCH_LIMIT).stream()
            .filter(hit -> hit.title() != null
                && TextMatch.titleWithinQuery(hit.title(), query)
                && SingleBookFilter.isSingleBook(hit.title()))
            .min(Comparator.comparing(SourceBook::publicationYear, Comparator.nullsLast(Comparator.naturalOrder())))
            .ifPresent(this::stage);
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
