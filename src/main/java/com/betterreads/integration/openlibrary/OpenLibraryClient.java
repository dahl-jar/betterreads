package com.betterreads.integration.openlibrary;

import com.betterreads.catalog.service.BookSourceClient;
import com.betterreads.catalog.service.SourceBook;
import java.util.Optional;

/** OpenLibrary HTTP client. */
public interface OpenLibraryClient extends BookSourceClient {

    /**
     * Returns the book for the given OpenLibrary work key, or empty if none matches.
     *
     * @param workKey work key with the {@code /works/} prefix stripped (e.g. {@code OL45883W})
     */
    Optional<SourceBook> fetchByWorkKey(String workKey);
}
