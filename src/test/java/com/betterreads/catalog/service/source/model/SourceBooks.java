package com.betterreads.catalog.service.source.model;

import java.util.List;

/**
 * Shared {@link SourceBook} fixtures. Tests derive variants with {@link SourceBook#toBuilder()}.
 */
public final class SourceBooks {

    private static final int DUNE_YEAR = 1965;

    private SourceBooks() {
    }

    /** A complete Dune with every field promotion requires present. */
    public static SourceBook dune() {
        return SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
            .isbn13("9780441013593")
            .openLibraryWorkKey("OL893415W")
            .title("Dune")
            .description("Paul Atreides leads the Fremen against the Padishah Empire on Arrakis.")
            .coverUrl("https://covers.example/dune.jpg")
            .publicationYear(DUNE_YEAR)
            .authors(List.of(SourceAuthor.ofName("Frank Herbert")))
            .build();
    }
}
