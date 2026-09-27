package com.betterreads.errors;
import java.io.Serial;

/** User acts on a resource they don't own, gets a 403. */
public class ForbiddenException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public ForbiddenException(final String message) {
        super(message);
    }
}
