package com.betterreads.features.search;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/** Full-text search over the book catalog. */
interface BookSearchService {

    SearchOutcome search(String query, int offset, int limit);

    Optional<BookSearchDocument> hitFor(String query, String bookId);

    /** Upserts by bookId. */
    void index(Collection<BookSearchDocument> documents);

    Set<String> indexedIds(int pageSize);

    void deleteAll(Collection<String> ids);
}
