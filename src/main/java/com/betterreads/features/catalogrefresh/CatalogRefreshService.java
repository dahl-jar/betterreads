package com.betterreads.features.catalogrefresh;

import com.betterreads.book.AuthorRepository;
import com.betterreads.bookdiscovery.BookDiscovery;
import com.betterreads.logging.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientException;

/**
 * Re-resolves known authors and series, so a new book by a known author shows up without anyone
 * searching for it.
 */
@Service
class CatalogRefreshService {

    private static final Logger LOG = LoggerFactory.getLogger(CatalogRefreshService.class);

    private final AuthorRepository authors;

    private final BookDiscovery discovery;

    private final DueSeriesRefresher dueSeries;

    private final CatalogRefreshProperties properties;

    public CatalogRefreshService(
        final AuthorRepository authors,
        final BookDiscovery discovery,
        final DueSeriesRefresher dueSeries,
        final CatalogRefreshProperties properties
    ) {
        this.authors = authors;
        this.discovery = discovery;
        this.dueSeries = dueSeries;
        this.properties = properties;
    }

    /**
     * each search-and-stage call writes in its own transaction, and a read-only transaction around
     * the run makes Postgres reject those inserts with {@code 25006}
     */
    public void refresh() {
        if (properties.authorsEnabled()) {
            authors.findPrimaryCreditNames().forEach(this::refreshAuthor);
        }
        if (properties.seriesEnabled()) {
            dueSeries.refresh();
        }
    }

    private void refreshAuthor(final String name) {
        try {
            discovery.searchAuthorAndStage(name);
        } catch (WebClientException | DataAccessException ex) {
            LOG.warn("catalog.refresh failed author={} ({}), skipping it",
                LogSanitizer.forLog(name), ex.getClass().getSimpleName());
        }
    }
}
