package com.betterreads.clients.googlebooks;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

/** Cover image URLs for a Google Books volume, scoped to that edition. */
@JsonIgnoreProperties(ignoreUnknown = true)
record ImageLinks(
    @Nullable String thumbnail,
    @Nullable String smallThumbnail
) {
}
