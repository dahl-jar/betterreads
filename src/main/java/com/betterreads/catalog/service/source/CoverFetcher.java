package com.betterreads.catalog.service.source;

import java.util.Optional;

/**
 * Downloads the bytes behind an external cover URL.
 */
@FunctionalInterface
public interface CoverFetcher {

    /** Returns the fetched image, or empty when the URL cannot be downloaded as an image. */
    Optional<FetchedImage> fetch(String url);
}
