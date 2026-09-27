package com.betterreads.clients.openlibrary;

import com.betterreads.booksource.BookSourceClient;
import com.betterreads.booksource.SourceBook;
import java.util.List;
import java.util.Optional;

public interface OpenLibraryClient extends BookSourceClient {

    /** takes the bare key, e.g. {@code OL45883W} */
    Optional<SourceBook> fetchByWorkKey(String workKey);

    /** One book per work, so a series query keeps all its volumes. Description and subjects are null. */
    List<SourceBook> search(String query, int limit);
}
