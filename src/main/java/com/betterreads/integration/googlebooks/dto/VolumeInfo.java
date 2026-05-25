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
) { }
