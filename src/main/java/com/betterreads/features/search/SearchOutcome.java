package com.betterreads.features.search;

/**
 * degraded is true when Meilisearch was unreachable, so the page holds only exact catalog matches and
 * a caller can tell an outage from a real zero-hit answer.
 */
record SearchOutcome(BookSearchResult result, boolean degraded) {
}
