package com.betterreads.search.service;

import com.betterreads.search.dto.BookSearchDocument;
import com.betterreads.search.dto.BookSearchResult;
import com.betterreads.search.dto.SearchOutcome;
import java.util.Collection;

/**
 * Contract for catalog search backed by Meilisearch.
 */
public interface BookSearchService {

    /**
     * Runs a typo-tolerant query and returns the paged matches.
     *
     * @param query user-supplied search string
     * @param offset zero-based offset of the first hit
     * @param limit page size, capped by the implementation
     */
    BookSearchResult search(String query, int offset, int limit);

    /**
     * Runs the query and reports whether an empty result came from a backend outage.
     *
     * @param query user-supplied search string
     * @param offset zero-based offset of the first hit
     * @param limit page size, capped by the implementation
     */
    SearchOutcome searchOutcome(String query, int offset, int limit);

    /**
     * Inserts or replaces the given documents in the books index.
     */
    void index(Collection<BookSearchDocument> documents);

    /**
     * Removes the document with the given id from the books index.
     */
    void remove(String bookId);
}
