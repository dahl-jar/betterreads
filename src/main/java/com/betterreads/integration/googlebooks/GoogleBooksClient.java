package com.betterreads.integration.googlebooks;

import com.betterreads.catalog.service.source.port.BookSourceClient;
import com.betterreads.catalog.service.source.model.SourceBook;
import java.util.Optional;

/** Google Books REST API client. */
public interface GoogleBooksClient extends BookSourceClient {

    /**
     * Returns the book for the given Google Books volume id, or empty if none matches.
     *
     * @param volumeId Google Books volume id (e.g. {@code wrOQLV6xB-wC})
     */
    Optional<SourceBook> fetchByVolumeId(String volumeId);
}
