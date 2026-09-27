package com.betterreads.clients.openlibrary;

import java.util.List;

import com.betterreads.booksource.NullableLists;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

/** Top-level {@code search.json} response. */
@JsonIgnoreProperties(ignoreUnknown = true)
record OpenLibrarySearchResponse(
    @Nullable List<OpenLibrarySearchDoc> docs
) {

    public OpenLibrarySearchResponse {
        docs = NullableLists.copyOf(docs);
    }

    @Override
    @Nullable
    public List<OpenLibrarySearchDoc> docs() {
        return NullableLists.copyOf(docs);
    }
}
