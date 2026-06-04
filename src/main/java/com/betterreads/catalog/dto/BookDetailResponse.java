package com.betterreads.catalog.dto;

import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Book detail returned by {@code GET /api/v1/books/{key}}.
 *
 * <p>{@code complete} is true for a promoted catalog book and false for a staging seed served while
 * enrichment is still running, so the client knows whether the missing fields will fill in.
 *
 * @param key the lookup key, a source identifier shared with search results
 * @param complete true when the book is promoted, false when it is still a staging seed
 */
public record BookDetailResponse(
    String key,
    boolean complete,
    String title,
    @Nullable String subtitle,
    List<String> authors,
    @Nullable String description,
    @Nullable String coverUrl,
    @Nullable Integer firstPublishYear,
    @Nullable String isbn,
    @Nullable Integer pageCount,
    @Nullable String language,
    @Nullable BigDecimal averageRating,
    @Nullable Integer ratingCount,
    @Nullable String seriesName,
    @Nullable Integer seriesPosition,
    List<String> subjects,
    List<String> awards
) {

    public BookDetailResponse {
        authors = List.copyOf(authors);
        subjects = List.copyOf(subjects);
        awards = List.copyOf(awards);
    }

    @Override
    public List<String> authors() {
        return List.copyOf(authors);
    }

    @Override
    public List<String> subjects() {
        return List.copyOf(subjects);
    }

    @Override
    public List<String> awards() {
        return List.copyOf(awards);
    }

    /** Returns a builder for the book with the given key and completeness. */
    public static Builder builder(final String key, final boolean complete) {
        return new Builder(key, complete);
    }

    /**
     * Builds a {@link BookDetailResponse} field by field so the mapper sets each field by name
     * instead of by position, where two same-typed fields could be swapped unnoticed.
     */
    @SuppressWarnings({"PMD.TooManyMethods", "PMD.AvoidFieldNameMatchingMethodName"})
    public static final class Builder {

        private final String key;
        private final boolean complete;
        private String title = "";
        private @Nullable String subtitle;
        private List<String> authors = List.of();
        private @Nullable String description;
        private @Nullable String coverUrl;
        private @Nullable Integer firstPublishYear;
        private @Nullable String isbn;
        private @Nullable Integer pageCount;
        private @Nullable String language;
        private @Nullable BigDecimal averageRating;
        private @Nullable Integer ratingCount;
        private @Nullable String seriesName;
        private @Nullable Integer seriesPosition;
        private List<String> subjects = List.of();
        private List<String> awards = List.of();

        private Builder(final String key, final boolean complete) {
            this.key = key;
            this.complete = complete;
        }

        public Builder title(final String value) {
            this.title = value;
            return this;
        }

        public Builder subtitle(final @Nullable String value) {
            this.subtitle = value;
            return this;
        }

        public Builder authors(final List<String> value) {
            this.authors = List.copyOf(value);
            return this;
        }

        public Builder description(final @Nullable String value) {
            this.description = value;
            return this;
        }

        public Builder coverUrl(final @Nullable String value) {
            this.coverUrl = value;
            return this;
        }

        public Builder firstPublishYear(final @Nullable Integer value) {
            this.firstPublishYear = value;
            return this;
        }

        public Builder isbn(final @Nullable String value) {
            this.isbn = value;
            return this;
        }

        public Builder pageCount(final @Nullable Integer value) {
            this.pageCount = value;
            return this;
        }

        public Builder language(final @Nullable String value) {
            this.language = value;
            return this;
        }

        public Builder averageRating(final @Nullable BigDecimal value) {
            this.averageRating = value;
            return this;
        }

        public Builder ratingCount(final @Nullable Integer value) {
            this.ratingCount = value;
            return this;
        }

        public Builder seriesName(final @Nullable String value) {
            this.seriesName = value;
            return this;
        }

        public Builder seriesPosition(final @Nullable Integer value) {
            this.seriesPosition = value;
            return this;
        }

        public Builder subjects(final List<String> value) {
            this.subjects = List.copyOf(value);
            return this;
        }

        public Builder awards(final List<String> value) {
            this.awards = List.copyOf(value);
            return this;
        }

        public BookDetailResponse build() {
            return new BookDetailResponse(
                key, complete, title, subtitle, authors, description, coverUrl, firstPublishYear,
                isbn, pageCount, language, averageRating, ratingCount, seriesName, seriesPosition,
                subjects, awards);
        }
    }
}
