package com.betterreads.common.exception;

import java.io.Serial;

/**
 * Thrown when the request is well-formed but rejected for content reasons such as an unknown
 * or expired token. Mapped to {@code 400} by {@link GlobalExceptionHandler}.
 *
 * <p>Separate from {@link BusinessRuleException} ({@code 409}) so a bad input is distinguished
 * from a state conflict like duplicate registration.
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
