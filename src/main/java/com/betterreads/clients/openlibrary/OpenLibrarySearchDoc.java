package com.betterreads.clients.openlibrary;

import java.util.List;

import com.betterreads.booksource.NullableLists;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * One work-level result from OpenLibrary {@code search.json}.
 *
 * <p>{@code firstPublishYear} is the original edition's year.
 *
 * @param key work key including the {@code /works/} prefix, e.g. {@code /works/OL27482W}
 * @param coverId OpenLibrary cover id, {@code 0} when no cover exists
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record OpenLibrarySearchDoc(
    @Nullable String key,
    @Nullable String title,
    @Nullable String subtitle,
    @JsonProperty("author_name") @Nullable List<String> authorName,
    @JsonProperty("first_publish_year") @Nullable Integer firstPublishYear,
    @JsonProperty("cover_i") @Nullable Integer coverId,
    @Nullable List<String> language
) {

    public OpenLibrarySearchDoc {
        authorName = NullableLists.copyOf(authorName);
        language = NullableLists.copyOf(language);
    }

    @Override
    @Nullable
    public List<String> authorName() {
        return NullableLists.copyOf(authorName);
    }

    @Override
    @Nullable
    public List<String> language() {
        return NullableLists.copyOf(language);
    }
}
