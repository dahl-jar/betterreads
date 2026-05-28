package com.betterreads.integration.googlebooks.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

/**
 * One volume entry from a Google Books search or detail response.
 *
 * @param id Google Books volume id, e.g. {@code TtkxEAAAQBAJ}
 * @param volumeInfo per-edition metadata; can be missing on degenerate responses
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@SuppressWarnings("PMD.ShortVariable")
public record Volume(
    @Nullable String id,
    @Nullable VolumeInfo volumeInfo
) { }
