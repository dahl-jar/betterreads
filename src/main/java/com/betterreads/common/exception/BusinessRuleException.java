package com.betterreads.common.exception;
import java.io.Serial;

/**
 * Thrown when a request violates a domain rule (e.g. duplicate username, illegal state
 * transition). Mapped to 409 by {@link GlobalExceptionHandler}.
 */
public class BusinessRuleException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public BusinessRuleException(final String message) {
        super(message);
    }

    public BusinessRuleException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
