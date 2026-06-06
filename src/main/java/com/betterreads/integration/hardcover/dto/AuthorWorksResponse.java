package com.betterreads.integration.hardcover.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

/**
 * Hardcover author works response.
 *
 * <p>Chain: {@code data.authors[].contributions[].book}, ordered by readers.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AuthorWorksResponse(@Nullable Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(@Nullable List<Author> authors) {

        public Data {
            if (authors != null) {
                authors = List.copyOf(authors);
            }
        }

        @Override
        @Nullable
        public List<Author> authors() {
            return authors == null ? null : List.copyOf(authors);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Author(@Nullable String name, @Nullable List<Contribution> contributions) {

        public Author {
            if (contributions != null) {
                contributions = List.copyOf(contributions);
            }
        }

        @Override
        @Nullable
        public List<Contribution> contributions() {
            return contributions == null ? null : List.copyOf(contributions);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Contribution(@Nullable HardcoverBookNode book) { }
}
