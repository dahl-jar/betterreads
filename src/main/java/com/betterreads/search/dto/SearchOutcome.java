package com.betterreads.search.dto;

/**
 * Internal result of a search call: the paged hits plus whether the search backend was degraded.
 *
 * <p>{@code degraded} is true when the query fell back to an empty result because Meilisearch was
 * unreachable, so a caller can tell a real zero-hit answer from a backend outage.
 */
public record SearchOutcome(BookSearchResult result, boolean degraded) {
}
