package com.betterreads.integration.hardcover.dto;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

/**
 * Hardcover books-by-id response.
 *
 * <p>Chain: {@code data.books[]}; an unknown id yields an empty list.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BookByIdResponse(@Nullable Data data) {

    /** Returns the response's single book node, or empty when the id is unknown. */
    public static Optional<HardcoverBookNode> firstBook(final @Nullable BookByIdResponse response) {
        if (response == null || response.data() == null || response.data().books() == null) {
            return Optional.empty();
        }
        return response.data().books().stream().findFirst();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(@Nullable List<HardcoverBookNode> books) {

        public Data {
            if (books != null) {
                books = List.copyOf(books);
            }
        }

        @Override
        @Nullable
        public List<HardcoverBookNode> books() {
            return books == null ? null : List.copyOf(books);
        }
    }
}
