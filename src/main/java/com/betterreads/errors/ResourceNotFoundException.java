package com.betterreads.errors;
import java.io.Serial;

/** Missing resource, gets a 404. */
public class ResourceNotFoundException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException(final String message) {
        super(message);
    }
}
