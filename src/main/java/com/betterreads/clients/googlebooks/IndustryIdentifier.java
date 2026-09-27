package com.betterreads.clients.googlebooks;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

/**
 * One ISBN-family identifier attached to a Google Books volume.
 *
 * @param type one of {@code ISBN_10}, {@code ISBN_13}, {@code ISSN}, {@code OTHER}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record IndustryIdentifier(
    @Nullable String type,
    @Nullable String identifier
) { }
