package com.betterreads.images;

import java.util.Optional;

/**
 * Stores and reads image bytes in object storage.
 */
public interface ImageStore {

    Optional<Image> get(String key);

    /** Overwrites any existing object under the key. */
    void put(String key, byte[] bytes, String contentType);

    boolean exists(String key);
}
