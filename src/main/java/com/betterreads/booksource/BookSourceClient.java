package com.betterreads.booksource;

import java.util.Optional;

/**
 * External metadata source. A 4xx response comes back empty and infrastructure failures throw.
 */
public interface BookSourceClient {

    BookFieldSource source();

    Optional<SourceBook> fetchByIsbn(String isbn);

    /** author is the book's first author */
    Optional<SourceBook> fetchByTitleAuthor(String title, String author);
}
