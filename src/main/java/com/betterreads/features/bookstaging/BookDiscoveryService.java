package com.betterreads.features.bookstaging;

import com.betterreads.bookdiscovery.BookDiscovery;
import com.betterreads.booksource.SingleBookFilter;
import com.betterreads.booksource.SourceBook;
import com.betterreads.booksource.SourceSeries;
import com.betterreads.clients.hardcoverauthor.HardcoverAuthorClient;
import com.betterreads.clients.hardcoverseries.HardcoverSeriesClient;
import com.betterreads.clients.openlibrary.OpenLibraryClient;
import com.betterreads.text.TextMatch;

import java.util.Comparator;
import java.util.Optional;

import org.springframework.stereotype.Service;

@Service
class BookDiscoveryService implements BookDiscovery {

    private static final int FALLBACK_SEARCH_LIMIT = 5;

    private final HardcoverSeriesClient seriesClient;

    private final HardcoverAuthorClient authorClient;

    private final OpenLibraryClient openLibraryClient;

    private final BookStager stager;

    public BookDiscoveryService(
        final HardcoverSeriesClient seriesClient,
        final HardcoverAuthorClient authorClient,
        final OpenLibraryClient openLibraryClient,
        final BookStager stager
    ) {
        this.seriesClient = seriesClient;
        this.authorClient = authorClient;
        this.openLibraryClient = openLibraryClient;
        this.stager = stager;
    }

    @Override
    public void searchAndStage(final String query) {
        seriesClient.fetchSeries(query).ifPresentOrElse(
            this::stageSeries,
            () -> {
                if (!stageAuthor(query)) {
                    stageStandalone(query);
                }
            });
    }

    private void stageSeries(final SourceSeries series) {
        series.volumes().forEach(volume -> stager.stage(volume.book()));
        Optional.ofNullable(series.titleBook()).ifPresent(stager::stage);
    }

    @Override
    public void searchAuthorAndStage(final String query) {
        stageAuthor(query);
    }

    private boolean stageAuthor(final String query) {
        return authorClient.fetchAuthorWorks(query)
            .map(works -> {
                works.books().forEach(stager::stage);
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
            .ifPresent(stager::stage);
    }
}
