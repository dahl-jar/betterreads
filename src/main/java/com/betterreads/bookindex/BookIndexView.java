package com.betterreads.bookindex;

import java.math.BigDecimal;
import java.util.List;

import com.betterreads.booksource.SeriesEntry;
import org.jspecify.annotations.Nullable;

/**
 * A book as the search index sees it, with the cover resolved to the served URL.
 *
 * @param dedupKey public lookup key, also the index primary key
 * @param seriesName null for a standalone book
 * @param authors author names, sorted
 * @param subjects BISAC subjects
 * @param language ISO 639-1 code
 * @param averageRating rating from the catalog sources
 * @param ratingCount rating count from the catalog sources
 */
public record BookIndexView(
    String dedupKey,
    String title,
    @Nullable String subtitle,
    @Nullable String seriesName,
    @Nullable Double seriesPosition,
    List<SeriesEntry> series,
    List<String> authors,
    List<String> subjects,
    @Nullable String language,
    @Nullable String servedCoverUrl,
    @Nullable Integer firstPublishYear,
    @Nullable BigDecimal averageRating,
    @Nullable Integer ratingCount
) {

    public BookIndexView {
        series = List.copyOf(series);
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
