package com.betterreads.integration.openlibrary.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * Top-level OpenLibrary {@code search.json} response.
 *
 * @param numFound total matches across the index, not the size of {@code docs}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SearchResponse(
    @JsonProperty("numFound") @Nullable Integer numFound,
    @Nullable List<SearchDoc> docs
) {

    public SearchResponse {
        if (docs != null) {
            docs = List.copyOf(docs);
        }
    }

    @Override
    @Nullable
    public List<SearchDoc> docs() {
        return docs == null ? null : List.copyOf(docs);
    }
}
