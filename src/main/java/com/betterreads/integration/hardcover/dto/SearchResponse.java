package com.betterreads.integration.hardcover.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

/**
 * Hardcover GraphQL search response.
 *
 * <p>Chain: {@code data.search.results.hits[].document}. {@code results} is a typesense payload
 * the GraphQL schema types as {@code jsonb}; only the fields the mapper reads are bound here, the
 * rest are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SearchResponse(@Nullable Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(@Nullable Search search) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Search(@Nullable Results results) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Results(@Nullable List<Hit> hits) {

        public Results {
            if (hits != null) {
                hits = List.copyOf(hits);
            }
        }

        @Override
        @Nullable
        public List<Hit> hits() {
            return hits == null ? null : List.copyOf(hits);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Hit(@Nullable HardcoverDocument document) { }
}
