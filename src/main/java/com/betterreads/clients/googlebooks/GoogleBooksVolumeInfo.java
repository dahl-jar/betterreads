package com.betterreads.clients.googlebooks;

import java.util.List;

import com.betterreads.booksource.NullableLists;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

/** Edition-level metadata from Google Books. */
@JsonIgnoreProperties(ignoreUnknown = true)
record GoogleBooksVolumeInfo(
    @Nullable String title,
    @Nullable String subtitle,
    @Nullable List<String> authors,
    @Nullable String publishedDate,
    @Nullable String publisher,
    @Nullable Integer pageCount,
    @Nullable String language,
    @Nullable List<IndustryIdentifier> industryIdentifiers,
    @Nullable List<String> categories,
    @Nullable String description,
    @Nullable ImageLinks imageLinks
) {

    public GoogleBooksVolumeInfo {
        authors = NullableLists.copyOf(authors);
        industryIdentifiers = NullableLists.copyOf(industryIdentifiers);
        categories = NullableLists.copyOf(categories);
    }

    @Override
    @Nullable
    public List<String> authors() {
        return NullableLists.copyOf(authors);
    }

    @Override
    @Nullable
    public List<IndustryIdentifier> industryIdentifiers() {
        return NullableLists.copyOf(industryIdentifiers);
    }

    @Override
    @Nullable
    public List<String> categories() {
        return NullableLists.copyOf(categories);
    }
}
