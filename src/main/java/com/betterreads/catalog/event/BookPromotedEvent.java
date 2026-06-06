package com.betterreads.catalog.event;

/**
 * Published after a book is promoted into the catalog, carrying its dedup key.
 */
public record BookPromotedEvent(String dedupKey) {
}
