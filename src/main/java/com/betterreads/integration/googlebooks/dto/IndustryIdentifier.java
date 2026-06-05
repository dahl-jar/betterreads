package com.betterreads.integration.googlebooks.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

/**
 * One ISBN-family identifier attached to a Google Books volume.
 *
 * @param type one of {@code ISBN_10}, {@code ISBN_13}, {@code ISSN}, {@code OTHER}
 * @param identifier the identifier value as a string
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IndustryIdentifier(
    @Nullable String type,
    @Nullable String identifier
) { }
