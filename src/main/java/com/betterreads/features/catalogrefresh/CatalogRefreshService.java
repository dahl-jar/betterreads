package com.betterreads.features.catalogrefresh;

import com.betterreads.book.Author;
import com.betterreads.book.AuthorRepository;
import com.betterreads.book.BookRepository;
import com.betterreads.bookdiscovery.BookDiscovery;
import com.betterreads.logging.LogSanitizer;
import java.util.List;
import java.util.function.Consumer;
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

    private final BookRepository books;

    private final BookDiscovery discovery;

    public CatalogRefreshService(
        final AuthorRepository authors,
        final BookRepository books,
        final BookDiscovery discovery
    ) {
        this.authors = authors;
        this.books = books;
        this.discovery = discovery;
    }

    /**
     * each search-and-stage call writes in its own transaction, and a read-only transaction around
     * the run makes Postgres reject those inserts with {@code 25006}
     */
    public void refresh() {
        final List<String> authorNames = authors.findAll().stream().map(Author::getName).toList();
        final List<String> seriesNames = books.findDistinctSeriesNames();
        LOG.info("catalog.refresh re-resolving authors={} series={}", authorNames.size(), seriesNames.size());
        authorNames.forEach(name -> resolve(name, discovery::searchAuthorAndStage));
        seriesNames.forEach(name -> resolve(name, discovery::searchAndStage));
    }

    private void resolve(final String name, final Consumer<String> resolver) {
        try {
            resolver.accept(name);
        } catch (WebClientException | DataAccessException ex) {
            LOG.warn("catalog.refresh failed name={} ({}), skipping it",
                LogSanitizer.forLog(name), ex.getClass().getSimpleName());
        }
    }
}
