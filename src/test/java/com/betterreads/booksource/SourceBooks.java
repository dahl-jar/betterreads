package com.betterreads.booksource;

import com.betterreads.testsupport.Books;

import java.util.List;

public final class SourceBooks {

    private static final int DUNE_YEAR = 1965;

    private SourceBooks() {
    }

    /** Dune with every field promotion requires */
    public static SourceBook dune() {
        return SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
            .isbn13(Books.DUNE_ISBN)
            .openLibraryWorkKey(Books.DUNE_KEY)
            .title(Books.DUNE_TITLE)
            .description("Paul Atreides leads the Fremen against the Padishah Empire on Arrakis.")
            .coverUrl("https://covers.example/dune.jpg")
            .publicationYear(DUNE_YEAR)
            .authors(List.of(SourceAuthor.ofName(Books.DUNE_AUTHOR)))
            .build();
    }
}
