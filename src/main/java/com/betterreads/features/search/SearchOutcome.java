package com.betterreads.features.search;

/**
 * degraded is true when Meilisearch was unreachable and the empty page is a fallback, so a caller
 * can tell an outage from a real zero-hit answer.
 */
record SearchOutcome(BookSearchResult result, boolean degraded) {
}
