package com.betterreads.search.dto;

import com.betterreads.common.dto.Paged;

import java.util.List;

/**
 * Paged search response returned to the API.
 *
 * @param hits matching documents in result order
 * @param totalHits total number of matches across all pages
 * @param offset zero-based offset of the first hit in this page
 * @param limit page size requested
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
