package com.betterreads.features.search;

import java.util.Collection;
import java.util.Optional;

/** Full-text search over the book catalog. */
interface BookSearchService {

    SearchOutcome search(String query, int offset, int limit);

    Optional<BookSearchDocument> hitFor(String query, String bookId);

    /** Upserts by bookId. */
    void index(Collection<BookSearchDocument> documents);
}
