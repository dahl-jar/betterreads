package com.betterreads.errors;
import java.io.Serial;

/** Request breaks a domain rule, like a duplicate username, and gets a 409. */
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
