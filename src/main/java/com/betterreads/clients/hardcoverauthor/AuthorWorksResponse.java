package com.betterreads.clients.hardcoverauthor;

import com.betterreads.booksource.NullableLists;
import com.betterreads.clients.hardcover.HardcoverBookNode;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

/** Shape is {@code data.authors[].contributions[].book}, with contributions ordered by readers. */
@JsonIgnoreProperties(ignoreUnknown = true)
record AuthorWorksResponse(@Nullable Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(@Nullable List<Author> authors) {

        public Data {
            authors = NullableLists.copyOf(authors);
        }

        @Override
        @Nullable
        public List<Author> authors() {
            return NullableLists.copyOf(authors);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Author(@Nullable List<Contribution> contributions) {

        public Author {
            contributions = NullableLists.copyOf(contributions);
        }

        @Override
        @Nullable
        public List<Contribution> contributions() {
            return NullableLists.copyOf(contributions);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Contribution(@Nullable HardcoverBookNode book) { }
}
