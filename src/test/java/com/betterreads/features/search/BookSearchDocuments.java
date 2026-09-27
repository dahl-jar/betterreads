package com.betterreads.features.search;

import java.util.List;

final class BookSearchDocuments {

    static final String EYE_OF_THE_WORLD_ID = "9780000000001";

    private BookSearchDocuments() {
    }

    static BookSearchDocument eyeOfTheWorld() {
        return BookSearchDocument.builder(EYE_OF_THE_WORLD_ID)
            .title("The Eye of the World")
            .authors(List.of("Robert Jordan"))
            .build();
    }
}
