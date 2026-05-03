package com.betterreads.common.exception;
import java.io.Serial;

/**
 * Thrown when a requested resource cannot be found. Mapped to 404 by
 * {@link GlobalExceptionHandler}.
 */
public class ResourceNotFoundException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException(final String message) {
        super(message);
    }
}
