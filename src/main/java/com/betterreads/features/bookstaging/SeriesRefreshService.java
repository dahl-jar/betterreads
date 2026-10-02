package com.betterreads.features.bookstaging;

import java.util.List;

import com.betterreads.book.BookRepository;
import com.betterreads.bookdiscovery.SeriesRefresh;
import com.betterreads.booksource.SourceBook;
import com.betterreads.booksource.SourceSeriesVolume;
import com.betterreads.clients.hardcoverseries.HardcoverSeriesClient;
import org.springframework.stereotype.Service;

@Service
class SeriesRefreshService implements SeriesRefresh {

    private final HardcoverSeriesClient seriesClient;

    private final BookRepository books;

    private final BookStager stager;

    SeriesRefreshService(
        final HardcoverSeriesClient seriesClient, final BookRepository books, final BookStager stager) {
        this.seriesClient = seriesClient;
        this.books = books;
        this.stager = stager;
    }

    @Override
    public int refresh(final String seriesName) {
        final List<SourceBook> unverified = seriesClient.fetchSeries(seriesName).stream()
            .flatMap(series -> series.volumes().stream())
            .map(SourceSeriesVolume::book)
            .filter(book -> !hasVerifiedSeries(book))
            .toList();
        unverified.forEach(stager::stage);
        return unverified.size();
    }

    private boolean hasVerifiedSeries(final SourceBook book) {
        final String hardcoverId = book.hardcoverId();
        return hardcoverId != null && books.existsSeriesVerifiedByHardcoverId(hardcoverId);
    }
}
