package com.betterreads.clients.googlebooks;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

/**
 * One volume entry from a Google Books search or detail response.
 *
 * @param id Google Books volume id, e.g. {@code TtkxEAAAQBAJ}
 * @param volumeInfo per-edition metadata, missing on a partial response
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record GoogleBooksVolume(
    @Nullable String id,
    @Nullable GoogleBooksVolumeInfo volumeInfo
) { }
