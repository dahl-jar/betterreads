package com.betterreads.features.search;

import com.betterreads.web.Paged;

import java.util.List;

/**
 * One page of search hits.
 *
 * @param totalHits matches across all pages
 * @param offset zero-based offset of the first hit
 */
public record BookSearchResult(
    List<BookSearchDocument> hits,
    long totalHits,
    int offset,
    int limit
) implements Paged<BookSearchDocument> {

    public BookSearchResult {
        hits = List.copyOf(hits);
    }

    @Override
    public List<BookSearchDocument> hits() {
        return List.copyOf(hits);
    }

    @Override
    public List<BookSearchDocument> items() {
        return hits();
    }

    @Override
    public long total() {
        return totalHits;
    }
}
