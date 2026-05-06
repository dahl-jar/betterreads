package com.betterreads.common.exception;

import java.io.Serial;

/**
 * Thrown when the request payload was syntactically valid but semantically rejected (e.g. an
 * unknown, expired, or already-consumed token). Mapped to {@code 400} by
 * {@link GlobalExceptionHandler}.
 *
 * <p>{@link BusinessRuleException} stays mapped to {@code 409} for state conflicts such as
 * duplicate registration. The split keeps "your input was bad" distinct from "your input is
 * fine but conflicts with current state" so clients can react accordingly.
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
