package com.betterreads.integration.googlebooks.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

/** Edition-level metadata from Google Books. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VolumeInfo(
    @Nullable String title,
    @Nullable String subtitle,
    @Nullable List<String> authors,
    @Nullable String publishedDate,
    @Nullable String publisher,
    @Nullable Integer pageCount,
    @Nullable String language,
    @Nullable List<IndustryIdentifier> industryIdentifiers,
    @Nullable List<String> categories,
    @Nullable Double averageRating,
    @Nullable Integer ratingsCount,
    @Nullable String description
) {

    public VolumeInfo {
        if (authors != null) {
            authors = List.copyOf(authors);
        }
        if (industryIdentifiers != null) {
            industryIdentifiers = List.copyOf(industryIdentifiers);
        }
        if (categories != null) {
            categories = List.copyOf(categories);
        }
    }

    @Override
    @Nullable
    public List<String> authors() {
        return authors == null ? null : List.copyOf(authors);
    }

    @Override
    @Nullable
    public List<IndustryIdentifier> industryIdentifiers() {
        return industryIdentifiers == null ? null : List.copyOf(industryIdentifiers);
    }

    @Override
    @Nullable
    public List<String> categories() {
        return categories == null ? null : List.copyOf(categories);
    }
}
