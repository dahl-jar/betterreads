package com.betterreads.integration.hardcover.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * One book hit from Hardcover's search index.
 *
 * <p>The top hit is often a comic, audio, or stub edition, so the client picks the canonical work
 * by {@code usersReadCount} rather than rank. Hardcover's JSON is snake_case, mapped to these
 * camelCase components by {@link JsonNaming}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
@SuppressWarnings("PMD.ShortVariable")
public record HardcoverDocument(
    @Nullable String id,
    @Nullable String title,
    @Nullable String description,
    @Nullable Integer releaseYear,
    @Nullable Integer pages,
    @Nullable Double rating,
    @Nullable Integer ratingsCount,
    @Nullable Integer usersReadCount,
    @Nullable List<String> authorNames,
    @Nullable List<String> isbns,
    @Nullable List<String> genres,
    @Nullable Image image,
    @Nullable FeaturedSeries featuredSeries
) {

    public HardcoverDocument {
        authorNames = copyOrNull(authorNames);
        isbns = copyOrNull(isbns);
        genres = copyOrNull(genres);
    }

    @Override
    @Nullable
    public List<String> authorNames() {
        return copyOrNull(authorNames);
    }

    @Override
    @Nullable
    public List<String> isbns() {
        return copyOrNull(isbns);
    }

    @Override
    @Nullable
    public List<String> genres() {
        return copyOrNull(genres);
    }

    @Nullable
    private static List<String> copyOrNull(final @Nullable List<String> values) {
        return values == null ? null : List.copyOf(values);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Image(@Nullable String url) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FeaturedSeries(@Nullable Double position, @Nullable Series series) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Series(@Nullable String name) { }
    }
}
