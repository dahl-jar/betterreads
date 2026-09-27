package com.betterreads.errors;

import java.io.Serial;

/** Well-formed request rejected for its content, like an unknown or expired token. Gets a 400. */
public class InvalidRequestException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidRequestException(final String message) {
        super(message);
    }
}
