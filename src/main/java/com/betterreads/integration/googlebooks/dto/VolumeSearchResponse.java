package com.betterreads.integration.googlebooks.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

/**
 * Top-level Google Books {@code /volumes} search response.
 *
 * @param totalItems result count Google reports for the query (often inflated; see API docs)
 * @param items volumes Google returned for this page, or null when the query had no hits
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VolumeSearchResponse(
    int totalItems,
    @Nullable List<Volume> items
) {

    public VolumeSearchResponse {
        if (items != null) {
            items = List.copyOf(items);
        }
    }

    @Override
    @Nullable
    public List<Volume> items() {
        return items == null ? null : List.copyOf(items);
    }
}
