package com.betterreads.images;

import com.betterreads.book.Book;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** Builds cover URLs on this API's image endpoint so the browser never requests the external source. */
@Component
public class CoverImages {

    private static final String COVER_PATH_PREFIX = "/api/v1/images/covers/";

    private final String publicBaseUrl;

    public CoverImages(final ImageProperties properties) {
        this.publicBaseUrl = stripTrailingSlash(properties.publicBaseUrl());
    }

    /** Returns null when the book has no source cover. */
    public @Nullable String servedUrl(final String dedupKey, final @Nullable String sourceCoverUrl) {
        if (sourceCoverUrl == null || sourceCoverUrl.isBlank()) {
            return null;
        }
        return publicBaseUrl + COVER_PATH_PREFIX + dedupKey + "?v=" + CoverVersion.of(sourceCoverUrl);
    }

    public @Nullable String servedUrl(final Book book) {
        return servedUrl(book.getDedupKey(), book.getCoverUrl());
    }

    private static String stripTrailingSlash(final String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
