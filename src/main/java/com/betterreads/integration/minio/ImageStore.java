package com.betterreads.integration.minio;

import java.util.Optional;

/**
 * Stores and reads image bytes in object storage.
 */
public interface ImageStore {

    /** Returns the stored image for the key, or empty when none is stored. */
    Optional<StoredImage> get(String key);

    /** Stores the image bytes under the key, overwriting any existing object. */
    void put(String key, byte[] bytes, String contentType);

    /** Returns true when an object is stored under the key. */
    boolean exists(String key);
}
