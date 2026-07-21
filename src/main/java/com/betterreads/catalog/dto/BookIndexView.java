package com.betterreads.catalog.dto;

import java.math.BigDecimal;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * A book as the search index sees it, with the cover already resolved to the served URL. Carries
 * the raw rating average and count; search computes its own popularity score from them.
 *
 * @param dedupKey the public lookup key, used as the index primary key
 * @param title the book title
 * @param subtitle the book subtitle, null when none
 * @param seriesName the series name, null when the book is standalone
 * @param seriesPosition the book's position within its series, null when standalone
 * @param authors the author names, sorted
 * @param subjects the BISAC subjects
 * @param language the ISO 639-1 language code, null when unknown
 * @param servedCoverUrl the cover URL the API serves, null when the book has no cover
 * @param firstPublishYear the first publication year, null when unknown
 * @param averageRating the source average rating, null when unrated
 * @param ratingCount the source rating count, null when unrated
 */
public record BookIndexView(
    String dedupKey,
    String title,
    @Nullable String subtitle,
    @Nullable String seriesName,
    @Nullable Integer seriesPosition,
    List<String> authors,
    List<String> subjects,
    @Nullable String language,
    @Nullable String servedCoverUrl,
    @Nullable Integer firstPublishYear,
    @Nullable BigDecimal averageRating,
    @Nullable Integer ratingCount
) {

    public BookIndexView {
        authors = List.copyOf(authors);
        subjects = List.copyOf(subjects);
    }

    @Override
    public List<String> authors() {
        return List.copyOf(authors);
    }

    @Override
    public List<String> subjects() {
        return List.copyOf(subjects);
    }
}
