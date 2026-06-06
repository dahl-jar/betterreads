package com.betterreads.catalog.refresh;

import com.betterreads.catalog.entity.Author;
import com.betterreads.catalog.repository.AuthorRepository;
import com.betterreads.catalog.repository.BookRepository;
import com.betterreads.catalog.service.pipeline.CatalogSearchService;
import java.util.List;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClientException;

/**
 * Re-resolves the authors and series already in the catalog against the discovery sources, so a new
 * book by a known author is added without waiting for a user to search that author again.
 *
 * <p>This bypasses the search-miss dedup window on purpose: it is the deliberate refresh that the
 * dedup window defers. One failing author or series is logged and skipped so the rest still run.
 */
@Service
public class CatalogRefreshService {

    private static final Logger LOG = LoggerFactory.getLogger(CatalogRefreshService.class);

    private final AuthorRepository authors;

    private final BookRepository books;

    private final CatalogSearchService catalogSearch;

    public CatalogRefreshService(
        final AuthorRepository authors,
        final BookRepository books,
        final CatalogSearchService catalogSearch
    ) {
        this.authors = authors;
        this.books = books;
        this.catalogSearch = catalogSearch;
    }

    /** Re-resolves every known author and series, staging any new books they now have. */
    @Transactional(readOnly = true)
    public void refreshKnownAuthorsAndSeries() {
        final List<String> authorNames = authors.findAll().stream().map(Author::getName).toList();
        final List<String> seriesNames = books.findDistinctSeriesNames();
        LOG.info("catalog.refresh re-resolving authors={} series={}", authorNames.size(), seriesNames.size());
        authorNames.forEach(name -> resolve(name, catalogSearch::searchAuthorAndStage));
        seriesNames.forEach(name -> resolve(name, catalogSearch::searchAndStage));
    }

    private void resolve(final String name, final Consumer<String> resolver) {
        try {
            resolver.accept(name);
        } catch (WebClientException | DataAccessException ex) {
            LOG.warn("catalog.refresh failed for one entry ({}), skipping it", ex.getClass().getSimpleName());
        }
    }
}
