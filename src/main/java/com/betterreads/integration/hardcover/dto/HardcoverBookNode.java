package com.betterreads.integration.hardcover.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * A {@code books} node from a Hardcover series or author enumeration.
 *
 * <p>Carries the fields both enumerations read: title, rating, year, cover, description, language,
 * and authors. {@code canonicalId} differs from {@code id} on a translation or alternate edition;
 * {@code language} can be null on an edition that exists but has none set.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public record HardcoverBookNode(
    @Nullable Long id,
    @Nullable String title,
    @Nullable String description,
    @Nullable Double rating,
    @Nullable Integer ratingsCount,
    @Nullable Integer usersCount,
    @Nullable Integer releaseYear,
    @Nullable Long canonicalId,
    @Nullable Integer bookCategoryId,
    @Nullable Boolean compilation,
    @Nullable Boolean isPartialBook,
    @Nullable Image image,
    @Nullable Edition defaultPhysicalEdition,
    @Nullable List<Contribution> contributions,
    @Nullable List<SeriesMembership> bookSeries
) {

    public HardcoverBookNode {
        if (contributions != null) {
            contributions = List.copyOf(contributions);
        }
        if (bookSeries != null) {
            bookSeries = List.copyOf(bookSeries);
        }
    }

    @Override
    @Nullable
    public List<Contribution> contributions() {
        return contributions == null ? null : List.copyOf(contributions);
    }

    @Override
    @Nullable
    public List<SeriesMembership> bookSeries() {
        return bookSeries == null ? null : List.copyOf(bookSeries);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Image(@Nullable String url) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(SnakeCaseStrategy.class)
    public record Edition(@Nullable Language language, @Nullable ReadingFormat readingFormat) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Language(@Nullable String language) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReadingFormat(@Nullable String format) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Contribution(@Nullable Author author) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SeriesMembership(
        @Nullable Integer position, @Nullable Boolean featured, @Nullable Series series) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Series(@Nullable String name) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Author(@Nullable String name) { }
}
