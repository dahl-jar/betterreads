package com.betterreads.catalog.service;

import java.util.List;

import com.betterreads.integration.openlibrary.OpenLibraryClient;
import org.springframework.stereotype.Service;

/**
 * Turns a user search into staged candidates. Each matching book from the discovery source becomes
 * its own pending candidate, so a series query stages every volume. The poll later collects the
 * remaining sources for each candidate and promotes the complete ones.
 *
 * <p>OpenLibrary is the discovery source because its search returns the cleanest work-level hits.
 * The other sources are not searched here; they fill each candidate in during collection, keyed by
 * the candidate's identifiers, which avoids matching the same book across sources by fuzzy title.
 */
@Service
public class CatalogSearchService {

    private static final int SEARCH_LIMIT = 20;

    private final OpenLibraryClient openLibraryClient;

    private final SourceMerger merger;

    private final PendingBookService pendingBookService;

    public CatalogSearchService(
        final OpenLibraryClient openLibraryClient,
        final SourceMerger merger,
        final PendingBookService pendingBookService
    ) {
        this.openLibraryClient = openLibraryClient;
        this.merger = merger;
        this.pendingBookService = pendingBookService;
    }

    /** Stages one pending candidate per single book matching the query, skipping collections. */
    public void searchAndStage(final String query) {
        final List<SourceBook> hits = openLibraryClient.search(query, SEARCH_LIMIT);
        for (final SourceBook hit : hits) {
            if (isSingleBook(hit)) {
                pendingBookService.stage(merger.merge(List.of(hit)));
            }
        }
    }

    private static boolean isSingleBook(final SourceBook hit) {
        final String title = hit.title();
        return title != null && SingleBookFilter.isSingleBook(title);
    }
}
