package com.betterreads.catalog.service.source;

import java.util.List;

/**
 * An author and their books as Hardcover already knows them, in descending reader order.
 *
 * <p>Each book is English, one canonical work, with boxed sets removed.
 *
 * @param authorName author display name
 * @param books the author's books, most-read first
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
