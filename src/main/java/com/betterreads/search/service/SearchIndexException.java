package com.betterreads.search.service;

import java.io.Serial;

/** Thrown when writing to or removing from the Meilisearch index fails. */
public class SearchIndexException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public SearchIndexException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
