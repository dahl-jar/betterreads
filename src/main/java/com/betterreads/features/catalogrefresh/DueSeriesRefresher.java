package com.betterreads.features.catalogrefresh;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.betterreads.bookdiscovery.SeriesRefresh;
import com.betterreads.logging.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientException;

@Service
class DueSeriesRefresher {

    private static final Logger LOG = LoggerFactory.getLogger(DueSeriesRefresher.class);

    private final SeriesRefreshRepository refreshTimes;

    private final SeriesRefresh seriesRefresh;

    private final CatalogRefreshProperties properties;

    DueSeriesRefresher(
        final SeriesRefreshRepository refreshTimes,
        final SeriesRefresh seriesRefresh,
        final CatalogRefreshProperties properties
    ) {
        this.refreshTimes = refreshTimes;
        this.seriesRefresh = seriesRefresh;
        this.properties = properties;
    }

    void refresh() {
        int collected = 0;
        for (final String name : refreshTimes.findSeriesDueForRefresh()) {
            if (collected >= properties.maxBooksPerRun()) {
                break;
            }
            collected += booksCollectedFor(name);
            refreshTimes.markRefreshed(name, OffsetDateTime.now(ZoneOffset.UTC));
        }
        LOG.info("catalog.refresh series run collected books={}", collected);
    }

    private int booksCollectedFor(final String seriesName) {
        try {
            return seriesRefresh.refresh(seriesName);
        } catch (WebClientException | DataAccessException ex) {
            LOG.warn("catalog.refresh failed series={} ({}), skipping it",
                LogSanitizer.forLog(seriesName), ex.getClass().getSimpleName());
            return 0;
        }
    }
}
