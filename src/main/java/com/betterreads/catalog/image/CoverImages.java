package com.betterreads.catalog.image;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Builds the cover URL the API serves to clients, pointing at this service's image endpoint rather
 * than the external source, so the browser fetches covers only from here.
 *
 * <p>The URL is absolute on the configured public API origin: the SPA runs on a different host and
 * sets the value directly as an image source, so a root-relative path would resolve against the SPA
 * origin instead of this API.
 */
@Component
public class CoverImages {

    private static final String COVER_PATH_PREFIX = "/api/v1/images/covers/";

    private final String publicBaseUrl;

    public CoverImages(final ImageProperties properties) {
        this.publicBaseUrl = stripTrailingSlash(properties.publicBaseUrl());
    }

    /**
     * Returns the served cover URL for the book key, or null when the book has no source cover.
     *
     * <p>The URL carries a {@code v} token derived from the source cover URL, so it changes when the
     * cover changes. A browser then fetches the new cover rather than a year-cached old one, which is
     * what lets the response mark the bytes immutable.
     *
     * @param dedupKey the book lookup key
     * @param sourceCoverUrl the external cover URL the book was stored with, null or blank when none
     */
    public @Nullable String servedUrl(final String dedupKey, final @Nullable String sourceCoverUrl) {
        if (sourceCoverUrl == null || sourceCoverUrl.isBlank()) {
            return null;
        }
        return publicBaseUrl + COVER_PATH_PREFIX + dedupKey + "?v=" + CoverVersion.of(sourceCoverUrl);
    }

    private static String stripTrailingSlash(final String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
