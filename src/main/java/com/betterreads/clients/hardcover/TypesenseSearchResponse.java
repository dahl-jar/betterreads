package com.betterreads.clients.hardcover;

import java.util.List;

import com.betterreads.booksource.NullableLists;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

/**
 * Hardcover {@code search} response, read as {@code data.search.results.hits[].document}.
 *
 * <p>The GraphQL schema types {@code results} as a {@code jsonb} Typesense payload, so each query
 * binds its own document type as {@code D}.
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
            hits = NullableLists.copyOf(hits);
        }

        @Override
        @Nullable
        public List<Hit<D>> hits() {
            return NullableLists.copyOf(hits);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Hit<D>(@Nullable D document) { }
}
