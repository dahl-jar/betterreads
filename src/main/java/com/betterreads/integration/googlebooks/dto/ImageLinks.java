package com.betterreads.integration.googlebooks.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

/** Cover image URLs for a Google Books volume, scoped to that edition. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ImageLinks(
    @Nullable String thumbnail,
    @Nullable String smallThumbnail
) {
}
