package com.betterreads.catalog.event;

/**
 * Published when a book is promoted into the catalog, carrying its dedup key so a listener can index
 * it after the promotion commits.
 */
public record BookPromotedEvent(String dedupKey) {
}
