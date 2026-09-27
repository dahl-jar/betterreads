package com.betterreads.clients.hardcoverbook;

import com.betterreads.booksource.NullableLists;
import com.betterreads.clients.hardcover.HardcoverBookNode;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

/** Shape is {@code data.books[]}. An unknown id comes back as an empty list. */
@JsonIgnoreProperties(ignoreUnknown = true)
record BookByIdResponse(@Nullable Data data) {

    public static Optional<HardcoverBookNode> firstBook(final BookByIdResponse response) {
        return Optional.ofNullable(response.data())
            .map(Data::books)
            .flatMap(books -> books.stream().findFirst());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(@Nullable List<HardcoverBookNode> books) {

        public Data {
            books = NullableLists.copyOf(books);
        }

        @Override
        @Nullable
        public List<HardcoverBookNode> books() {
            return NullableLists.copyOf(books);
        }
    }
}
