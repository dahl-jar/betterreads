package com.betterreads.catalog.service;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Book metadata returned by a {@link BookSourceClient}.
 *
 * <p>Every field except {@code source} is nullable. For list fields, {@code null}
 * means the source did not return that field; an empty list means the source
 * returned the field with no values.
 */
public record SourceBook(
        BookFieldSource source,

        @Nullable String isbn13,
        @Nullable String openLibraryWorkKey,
        @Nullable String googleBooksVolumeId,
        @Nullable String wikidataQid,
        @Nullable String locLccn,
        @Nullable String hardcoverId,

        @Nullable String title,
        @Nullable String subtitle,
        @Nullable String description,
        @Nullable Integer publicationYear,
        @Nullable String publisher,
        @Nullable Integer pageCount,
        @Nullable String language,
        @Nullable String coverUrl,
        @Nullable List<String> authorNames,

        @Nullable List<String> rawSubjects,
        @Nullable List<String> rawCategories,

        @Nullable Double averageRating,
        @Nullable Integer ratingCount,
        @Nullable String seriesName,
        @Nullable Integer seriesPosition) {

    public SourceBook {
        authorNames = authorNames == null ? null : List.copyOf(authorNames);
        rawSubjects = rawSubjects == null ? null : List.copyOf(rawSubjects);
        rawCategories = rawCategories == null ? null : List.copyOf(rawCategories);
    }
}
