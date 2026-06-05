package com.betterreads.integration.hardcover.dto;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/** Extracts the non-null search documents from a {@link TypesenseSearchResponse}. */
public final class TypesenseHits {

    private TypesenseHits() {
    }

    /** Returns the documents in {@code data.search.results.hits}, dropping null entries. */
    public static <D> List<D> documents(final @Nullable TypesenseSearchResponse<D> response) {
        return Optional.ofNullable(response)
            .map(TypesenseSearchResponse::data)
            .map(TypesenseSearchResponse.Data::search)
            .map(TypesenseSearchResponse.Search::results)
            .map(TypesenseSearchResponse.Results::hits)
            .orElseGet(List::of)
            .stream()
            .map(TypesenseSearchResponse.Hit::document)
            .filter(document -> document != null)
            .toList();
    }
}
