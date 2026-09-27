package com.betterreads.clients.googlebooks;

import java.util.List;

import com.betterreads.booksource.NullableLists;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

/**
 * Top-level Google Books {@code /volumes} search response.
 *
 * @param items volumes Google returned for this page, or null when the query had no hits
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record GoogleBooksSearchResponse(
    @Nullable List<GoogleBooksVolume> items
) {

    public GoogleBooksSearchResponse {
        items = NullableLists.copyOf(items);
    }

    @Override
    @Nullable
    public List<GoogleBooksVolume> items() {
        return NullableLists.copyOf(items);
    }
}
