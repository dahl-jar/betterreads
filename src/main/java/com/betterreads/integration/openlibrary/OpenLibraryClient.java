package com.betterreads.integration.openlibrary;

import com.betterreads.catalog.service.source.BookSourceClient;
import com.betterreads.catalog.service.source.SourceBook;
import java.util.List;
import java.util.Optional;

/** OpenLibrary HTTP client. */
public interface OpenLibraryClient extends BookSourceClient {

    /**
     * Returns the book for the given OpenLibrary work key, or empty if none matches.
     *
     * @param workKey work key with the {@code /works/} prefix stripped (e.g. {@code OL45883W})
     */
    Optional<SourceBook> fetchByWorkKey(String workKey);

    /**
     * Returns up to {@code limit} books matching the query, each a distinct work, so a series
     * query keeps all its volumes. The results carry only the search-level fields; description and
     * subjects come from later per-book enrichment.
     */
    List<SourceBook> search(String query, int limit);
}
