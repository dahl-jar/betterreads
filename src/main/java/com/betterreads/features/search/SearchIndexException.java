package com.betterreads.features.search;

import java.io.Serial;

/** Thrown when writing to the Meilisearch index fails. */
class SearchIndexException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public SearchIndexException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
