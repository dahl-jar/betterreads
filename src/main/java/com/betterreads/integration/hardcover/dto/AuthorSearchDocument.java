package com.betterreads.integration.hardcover.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import tools.jackson.databind.annotation.JsonNaming;

/** One author hit from Hardcover's search index, carried in a {@link TypesenseSearchResponse}. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public record AuthorSearchDocument(
    @Nullable String id,
    @Nullable String name,
    @Nullable Integer booksCount
) { }
