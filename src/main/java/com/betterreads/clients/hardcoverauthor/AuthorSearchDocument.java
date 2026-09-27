package com.betterreads.clients.hardcoverauthor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import tools.jackson.databind.annotation.JsonNaming;

/** One author hit from Hardcover's search index. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
record AuthorSearchDocument(
    @Nullable String id,
    @Nullable String name,
    @Nullable Integer booksCount
) { }
