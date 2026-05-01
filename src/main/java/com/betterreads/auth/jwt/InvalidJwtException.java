package com.betterreads.auth.jwt;

import java.io.Serial;

/**
 * Thrown when a JWT cannot be parsed, has an invalid signature, or has expired.
 */
public class InvalidJwtException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidJwtException(final String message) {
        super(message);
    }

    public InvalidJwtException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
