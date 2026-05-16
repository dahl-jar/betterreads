package com.betterreads.catalog.service;

/**
 * Identifies a metadata source for provenance columns and trust-map keys.
 */
public enum BookFieldSource {
    OPEN_LIBRARY,
    GOOGLE_BOOKS,
    LOC,
    WIKIDATA,
    HARDCOVER
}
