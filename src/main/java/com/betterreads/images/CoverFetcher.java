package com.betterreads.images;

import java.util.Optional;

/**
 * Downloads the bytes behind an external cover URL.
 */
@FunctionalInterface
public interface CoverFetcher {

    /** Returns the fetched image, or empty when the URL cannot be downloaded as an image. */
    Optional<Image> fetch(String url);
}
