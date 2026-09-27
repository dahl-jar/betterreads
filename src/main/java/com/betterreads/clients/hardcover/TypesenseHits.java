package com.betterreads.clients.hardcover;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/** Reads the non-null documents out of a Hardcover search response. */
public final class TypesenseHits {

    private TypesenseHits() {
    }

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
