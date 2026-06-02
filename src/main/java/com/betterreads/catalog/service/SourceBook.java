package com.betterreads.catalog.service;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Book metadata returned by a {@link BookSourceClient}.
 *
 * <p>Every field except {@code source} is nullable. For list fields, {@code null}
 * means the source did not return that field; an empty list means the source
 * returned the field with no values.
 *
 * <p>Authors carry per-person identity via {@link SourceAuthor}. {@link #authorNames()} returns
 * just the names.
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
        @Nullable List<SourceAuthor> authors,

        @Nullable List<String> rawSubjects,
        @Nullable List<String> rawCategories,
        @Nullable List<String> awards,

        @Nullable Double averageRating,
        @Nullable Integer ratingCount,
        @Nullable String seriesName,
        @Nullable Integer seriesPosition) {

    public SourceBook {
        if (authors != null) {
            authors = List.copyOf(authors);
        }
        if (rawSubjects != null) {
            rawSubjects = List.copyOf(rawSubjects);
        }
        if (rawCategories != null) {
            rawCategories = List.copyOf(rawCategories);
        }
        if (awards != null) {
            awards = List.copyOf(awards);
        }
    }

    @Override
    @Nullable
    public List<SourceAuthor> authors() {
        return authors == null ? null : List.copyOf(authors);
    }

    @Override
    @Nullable
    public List<String> rawSubjects() {
        return rawSubjects == null ? null : List.copyOf(rawSubjects);
    }

    @Override
    @Nullable
    public List<String> rawCategories() {
        return rawCategories == null ? null : List.copyOf(rawCategories);
    }

    @Override
    @Nullable
    public List<String> awards() {
        return awards == null ? null : List.copyOf(awards);
    }

    /** Returns the author display names, or null when the source returned no authors. */
    @Nullable
    public List<String> authorNames() {
        return authors == null ? null : authors.stream().map(SourceAuthor::name).toList();
    }

    /** Returns a builder for the given source, with every other field unset. */
    public static Builder builder(final BookFieldSource source) {
        return new Builder(source);
    }

    /**
     * Builds a {@link SourceBook} field by field so a mapper sets only the fields its source
     * supplies, instead of passing positional nulls for the rest.
     */
    // PMD.TooManyMethods, PMD.NullAssignment: builder pattern, one setter per record component.
    @SuppressWarnings({
        "PMD.TooManyFields", "PMD.TooManyMethods", "PMD.ExcessivePublicCount",
        "PMD.AvoidFieldNameMatchingMethodName", "PMD.NullAssignment"
    })
    public static final class Builder {

        private final BookFieldSource source;
        private @Nullable String isbn13;
        private @Nullable String openLibraryWorkKey;
        private @Nullable String googleBooksVolumeId;
        private @Nullable String wikidataQid;
        private @Nullable String locLccn;
        private @Nullable String hardcoverId;
        private @Nullable String title;
        private @Nullable String subtitle;
        private @Nullable String description;
        private @Nullable Integer publicationYear;
        private @Nullable String publisher;
        private @Nullable Integer pageCount;
        private @Nullable String language;
        private @Nullable String coverUrl;
        private @Nullable List<SourceAuthor> authors;
        private @Nullable List<String> rawSubjects;
        private @Nullable List<String> rawCategories;
        private @Nullable List<String> awards;
        private @Nullable Double averageRating;
        private @Nullable Integer ratingCount;
        private @Nullable String seriesName;
        private @Nullable Integer seriesPosition;

        private Builder(final BookFieldSource source) {
            this.source = source;
        }

        public Builder isbn13(final @Nullable String value) {
            this.isbn13 = value;
            return this;
        }

        public Builder openLibraryWorkKey(final @Nullable String value) {
            this.openLibraryWorkKey = value;
            return this;
        }

        public Builder googleBooksVolumeId(final @Nullable String value) {
            this.googleBooksVolumeId = value;
            return this;
        }

        public Builder wikidataQid(final @Nullable String value) {
            this.wikidataQid = value;
            return this;
        }

        public Builder locLccn(final @Nullable String value) {
            this.locLccn = value;
            return this;
        }

        public Builder hardcoverId(final @Nullable String value) {
            this.hardcoverId = value;
            return this;
        }

        public Builder title(final @Nullable String value) {
            this.title = value;
            return this;
        }

        public Builder subtitle(final @Nullable String value) {
            this.subtitle = value;
            return this;
        }

        public Builder description(final @Nullable String value) {
            this.description = value;
            return this;
        }

        public Builder publicationYear(final @Nullable Integer value) {
            this.publicationYear = value;
            return this;
        }

        public Builder publisher(final @Nullable String value) {
            this.publisher = value;
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

        public Builder coverUrl(final @Nullable String value) {
            this.coverUrl = value;
            return this;
        }

        public Builder authors(final @Nullable List<SourceAuthor> value) {
            this.authors = value == null ? null : List.copyOf(value);
            return this;
        }

        public Builder rawSubjects(final @Nullable List<String> value) {
            this.rawSubjects = value == null ? null : List.copyOf(value);
            return this;
        }

        public Builder rawCategories(final @Nullable List<String> value) {
            this.rawCategories = value == null ? null : List.copyOf(value);
            return this;
        }

        public Builder awards(final @Nullable List<String> value) {
            this.awards = value == null ? null : List.copyOf(value);
            return this;
        }

        public Builder averageRating(final @Nullable Double value) {
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

        public SourceBook build() {
            return new SourceBook(
                source, isbn13, openLibraryWorkKey, googleBooksVolumeId, wikidataQid, locLccn,
                hardcoverId, title, subtitle, description, publicationYear, publisher, pageCount,
                language, coverUrl, authors, rawSubjects, rawCategories, awards, averageRating,
                ratingCount, seriesName, seriesPosition);
        }
    }
}
