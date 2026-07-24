package com.betterreads.catalog.service.source.model;

/** Identifies a metadata source for provenance columns and priority-chain keys. */
public enum BookFieldSource {
    OPEN_LIBRARY,
    GOOGLE_BOOKS,
    LOC,
    WIKIDATA,
    HARDCOVER,
    WIKIPEDIA,
    ITUNES,
    /** A pending row's stored values, merged as the fallback behind every live source. */
    STAGED
}
