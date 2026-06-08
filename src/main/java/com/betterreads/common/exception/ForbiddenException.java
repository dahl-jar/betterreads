package com.betterreads.common.exception;
import java.io.Serial;

/**
 * Thrown when an authenticated user tries to act on a resource they do not own. Mapped to 403 by
 * {@link GlobalExceptionHandler}.
 */
public class ForbiddenException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public ForbiddenException(final String message) {
        super(message);
    }
}
