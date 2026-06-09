package com.betterreads.integration.minio;

/**
 * Thrown when an object-storage read or write fails for a reason other than a missing object.
 */
public class ImageStoreException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ImageStoreException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
