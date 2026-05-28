package com.betterreads.integration.openlibrary.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * One work-level result from OpenLibrary {@code search.json}.
 *
 * <p>{@code firstPublishYear} is the original-edition year, the reason OpenLibrary is queried
 * alongside Google Books, which returns the most recent reprint year instead. {@code isbn} is
 * every edition's ISBN aggregated into one array (often 100+), not a single canonical value.
 * {@code coverId} of {@code 0} means no cover exists.
 *
 * @param key work key including the {@code /works/} prefix, e.g. {@code /works/OL27482W}
 * @param coverId OpenLibrary cover id, {@code 0} when no cover exists
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SearchDoc(
    @Nullable String key,
    @Nullable String title,
    @Nullable String subtitle,
    @JsonProperty("author_name") @Nullable List<String> authorName,
    @JsonProperty("first_publish_year") @Nullable Integer firstPublishYear,
    @JsonProperty("cover_i") @Nullable Integer coverId,
    @Nullable List<String> isbn,
    @Nullable List<String> language
) {

    public SearchDoc {
        if (authorName != null) {
            authorName = List.copyOf(authorName);
        }
        if (isbn != null) {
            isbn = List.copyOf(isbn);
        }
        if (language != null) {
            language = List.copyOf(language);
        }
    }

    @Override
    @Nullable
    public List<String> authorName() {
        return authorName == null ? null : List.copyOf(authorName);
    }

    @Override
    @Nullable
    public List<String> isbn() {
        return isbn == null ? null : List.copyOf(isbn);
    }

    @Override
    @Nullable
    public List<String> language() {
        return language == null ? null : List.copyOf(language);
    }
}
