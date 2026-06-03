package com.betterreads.catalog.service;

/**
 * The book fields that carry per-source provenance. The scheduled refresh re-queries a source for
 * the field it last supplied, so only the fields worth re-querying are tracked.
 */
public enum BookField {
    TITLE,
    DESCRIPTION,
    SUBJECTS,
    PUBLICATION_YEAR,
    COVER
}
