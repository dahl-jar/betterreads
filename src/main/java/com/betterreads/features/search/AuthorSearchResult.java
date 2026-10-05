package com.betterreads.features.search;

import java.util.List;

import com.betterreads.web.Paged;

public record AuthorSearchResult(
    List<AuthorSearchDocument> hits,
    long totalHits,
    int offset,
    int limit
) implements Paged<AuthorSearchDocument> {

    public AuthorSearchResult {
        hits = List.copyOf(hits);
    }

    @Override
    public List<AuthorSearchDocument> hits() {
        return List.copyOf(hits);
    }

    @Override
    public List<AuthorSearchDocument> items() {
        return hits();
    }

    @Override
    public long total() {
        return totalHits;
    }
}
