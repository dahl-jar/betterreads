package com.betterreads.catalog.service.source.model;

import java.util.List;

/**
 * An author and their books as Hardcover returns them, most-read first.
 *
 * <p>Each book is English and a single canonical work; boxed sets are removed.
 */
public record SourceAuthorWorks(String authorName, List<SourceBook> books) {

    public SourceAuthorWorks {
        books = List.copyOf(books);
    }

    @Override
    public List<SourceBook> books() {
        return List.copyOf(books);
    }
}
