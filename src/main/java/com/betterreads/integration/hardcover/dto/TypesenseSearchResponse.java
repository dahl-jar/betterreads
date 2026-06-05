package com.betterreads.integration.hardcover.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

/**
 * Hardcover {@code search} response shared by the book, series, and author queries.
 *
 * <p>Chain: {@code data.search.results.hits[].document}. {@code results} is a typesense payload the
 * GraphQL schema types as {@code jsonb}; {@code D} is the document type each query binds.
 *
 * @param <D> the search document type
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TypesenseSearchResponse<D>(@Nullable Data<D> data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data<D>(@Nullable Search<D> search) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Search<D>(@Nullable Results<D> results) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Results<D>(@Nullable List<Hit<D>> hits) {

        public Results {
            if (hits != null) {
                hits = List.copyOf(hits);
            }
        }

        @Override
        @Nullable
        public List<Hit<D>> hits() {
            return hits == null ? null : List.copyOf(hits);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Hit<D>(@Nullable D document) { }
}
