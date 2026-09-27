package com.betterreads.clients.hardcoverseries;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import tools.jackson.databind.annotation.JsonNaming;

/** One series hit from Hardcover's search index. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
record SeriesSearchDocument(
    @Nullable String id,
    @Nullable String name,
    @Nullable String authorName,
    @Nullable Integer readersCount
) { }
