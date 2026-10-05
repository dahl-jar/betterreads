package com.betterreads.features.search;

import java.util.Collection;
import java.util.List;

interface AuthorSearchService {

    AuthorSearchResult search(String query, int offset, int limit);

    void index(Collection<AuthorSearchDocument> documents);

    void deleteAll(Collection<Long> authorIds);

    boolean isEmpty();

    List<Long> indexedIds();
}
