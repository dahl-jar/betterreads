package com.betterreads.images;

/**
 * An object-storage read or write failed. A missing object comes back empty.
 */
public class ImageStoreException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ImageStoreException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
