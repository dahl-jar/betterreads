package com.betterreads.booksource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

/**
 * Book metadata from one source. A null list means the source did not send that field, an empty
 * list means it sent the field with no values.
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
        @Nullable List<String> awards,

        @Nullable Double averageRating,
        @Nullable Integer ratingCount,
        @Nullable String seriesName,
        @Nullable Integer seriesPosition) {

    public SourceBook {
        authors = NullableLists.copyOf(authors);
        rawSubjects = NullableLists.copyOf(rawSubjects);
        awards = NullableLists.copyOf(awards);
    }

    @Override
    @Nullable
    public List<SourceAuthor> authors() {
        return NullableLists.copyOf(authors);
    }

    @Override
    @Nullable
    public List<String> rawSubjects() {
        return NullableLists.copyOf(rawSubjects);
    }

    @Override
    @Nullable
    public List<String> awards() {
        return NullableLists.copyOf(awards);
    }

    @Nullable
    public List<String> authorNames() {
        return authors == null ? null : authors.stream().map(SourceAuthor::name).toList();
    }

    /** ISBN-13 goes first because most sources share it, so two sources for one book land on one staging row */
    public @Nullable String dedupKey() {
        return Stream.of(isbn13, openLibraryWorkKey, googleBooksVolumeId, hardcoverId, locLccn, wikidataQid)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    }

    public @Nullable BigDecimal roundedAverageRating() {
        return averageRating == null ? null : BigDecimal.valueOf(averageRating).setScale(2, RoundingMode.HALF_UP);
    }

    public static Builder builder(final BookFieldSource source) {
        return new Builder(source);
    }

    public Builder toBuilder() {
        return new Builder(source)
            .isbn13(isbn13)
            .openLibraryWorkKey(openLibraryWorkKey)
            .googleBooksVolumeId(googleBooksVolumeId)
            .wikidataQid(wikidataQid)
            .locLccn(locLccn)
            .hardcoverId(hardcoverId)
            .title(title)
            .subtitle(subtitle)
            .description(description)
            .publicationYear(publicationYear)
            .publisher(publisher)
            .pageCount(pageCount)
            .language(language)
            .coverUrl(coverUrl)
            .authors(authors)
            .rawSubjects(rawSubjects)
            .awards(awards)
            .averageRating(averageRating)
            .ratingCount(ratingCount)
            .seriesName(seriesName)
            .seriesPosition(seriesPosition);
    }

    /** the record has 22 components, so mappers set only the fields their source supplies */
    // PMD.TooManyFields, PMD.TooManyMethods, PMD.ExcessivePublicCount,
    // PMD.AvoidFieldNameMatchingMethodName: one nullable field and setter per record component.
    @SuppressWarnings({
        "PMD.TooManyFields", "PMD.TooManyMethods", "PMD.ExcessivePublicCount",
        "PMD.AvoidFieldNameMatchingMethodName"
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
            this.authors = NullableLists.copyOf(value);
            return this;
        }

        public Builder rawSubjects(final @Nullable List<String> value) {
            this.rawSubjects = NullableLists.copyOf(value);
            return this;
        }

        public Builder awards(final @Nullable List<String> value) {
            this.awards = NullableLists.copyOf(value);
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
                language, coverUrl, authors, rawSubjects, awards, averageRating,
                ratingCount, seriesName, seriesPosition);
        }
    }
}
