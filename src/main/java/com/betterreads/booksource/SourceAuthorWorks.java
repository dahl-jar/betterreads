package com.betterreads.booksource;

import java.util.List;

/** An author's Hardcover books, most-read first, English single works only. */
public record SourceAuthorWorks(String authorName, List<SourceBook> books) {

    public SourceAuthorWorks {
        books = List.copyOf(books);
    }
}
