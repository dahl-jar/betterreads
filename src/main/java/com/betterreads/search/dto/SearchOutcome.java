package com.betterreads.search.dto;

/**
 * Internal result of a search call: the paged hits plus whether the search backend was degraded.
 *
 * <p>{@code degraded} is true when the query fell back to an empty result because Meilisearch was
 * unreachable, so callers can tell a real zero-hit answer from a backend outage. A search miss
 * triggers staging only on a real zero-hit answer.
 *
 * @param result the paged hits returned to the API
 * @param degraded true when the empty result came from a Meilisearch failure
 */
public record SearchOutcome(BookSearchResult result, boolean degraded) {
}
