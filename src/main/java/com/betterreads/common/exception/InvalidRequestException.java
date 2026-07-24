package com.betterreads.common.exception;

import java.io.Serial;

/**
 * Thrown when the request is well-formed but rejected for content reasons such as an unknown
 * or expired token. Mapped to {@code 400} by {@link GlobalExceptionHandler}. A state conflict
 * such as a duplicate registration is a {@link BusinessRuleException}.
 */
public class InvalidRequestException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidRequestException(final String message) {
        super(message);
    }

    public InvalidRequestException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
