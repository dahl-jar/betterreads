package com.betterreads.search.dto;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Document shape stored in the Meilisearch books index.
 *
 * @param bookId stable primary key matching the local catalog id
 * @param title book title
 * @param subtitle book subtitle if any
 * @param seriesName name of the series the book belongs to, if any
 * @param authors author display names
 * @param subjects BISAC subjects assigned by the catalog mapper
 * @param language ISO 639-1 code
 * @param coverUrl cover image URL, so a result grid renders the cover without a per-hit detail fetch
 * @param publicationYear year of original publication
 * @param popularityScore tie-breaker for sort, 0 if unknown
 */
public record BookSearchDocument(
    String bookId,
    String title,
    @Nullable String subtitle,
    @Nullable String seriesName,
    List<String> authors,
    List<String> subjects,
    @Nullable String language,
    @Nullable String coverUrl,
    @Nullable Integer publicationYear,
    double popularityScore
) {

    /** Name of the {@code bookId} field, used as the Meilisearch index primary key. */
    public static final String PRIMARY_KEY = "bookId";

    public BookSearchDocument {
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

    /** Returns a builder for the document with the given book id. */
    public static Builder builder(final String bookId) {
        return new Builder(bookId);
    }

    /**
     * Builds a {@link BookSearchDocument} field by field so the mapper sets each field by name
     * instead of by position, where two same-typed fields could be swapped unnoticed.
     */
    @SuppressWarnings("PMD.AvoidFieldNameMatchingMethodName")
    public static final class Builder {

        private final String bookId;
        private String title = "";
        private @Nullable String subtitle;
        private @Nullable String seriesName;
        private List<String> authors = List.of();
        private List<String> subjects = List.of();
        private @Nullable String language;
        private @Nullable String coverUrl;
        private @Nullable Integer publicationYear;
        private double popularityScore;

        private Builder(final String bookId) {
            this.bookId = bookId;
        }

        public Builder title(final String value) {
            this.title = value;
            return this;
        }

        public Builder subtitle(final @Nullable String value) {
            this.subtitle = value;
            return this;
        }

        public Builder seriesName(final @Nullable String value) {
            this.seriesName = value;
            return this;
        }

        public Builder authors(final List<String> value) {
            this.authors = List.copyOf(value);
            return this;
        }

        public Builder subjects(final List<String> value) {
            this.subjects = List.copyOf(value);
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

        public Builder publicationYear(final @Nullable Integer value) {
            this.publicationYear = value;
            return this;
        }

        public Builder popularityScore(final double value) {
            this.popularityScore = value;
            return this;
        }

        public BookSearchDocument build() {
            return new BookSearchDocument(
                bookId, title, subtitle, seriesName, authors, subjects, language, coverUrl,
                publicationYear, popularityScore);
        }
    }
}
