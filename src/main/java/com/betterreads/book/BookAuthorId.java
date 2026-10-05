package com.betterreads.book;

import java.io.Serializable;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

public class BookAuthorId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Nullable
    private Long book;

    @Nullable
    private Long author;

    @Override
    public boolean equals(final Object other) {
        return other instanceof BookAuthorId id
            && Objects.equals(book, id.book)
            && Objects.equals(author, id.author);
    }

    @Override
    public int hashCode() {
        return Objects.hash(book, author);
    }
}
