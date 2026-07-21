package com.betterreads.catalog.service.source.port;

import java.util.Optional;

import com.betterreads.catalog.service.source.model.BookFieldSource;
import com.betterreads.catalog.service.source.model.SourceBook;

/**
 * Contract for an external metadata source.
 *
 * <p>4xx responses resolve to {@code Optional.empty()}; infrastructure
 * failures propagate as exceptions.
 */
public interface BookSourceClient {

    /** Returns the source identity. */
    BookFieldSource source();

    /** Returns the book for the given ISBN, or empty if none matches. */
    Optional<SourceBook> fetchByIsbn(String isbn);

    /** Returns the book for the given title and first author, or empty if none matches. */
    Optional<SourceBook> fetchByTitleAuthor(String title, String author);
}
