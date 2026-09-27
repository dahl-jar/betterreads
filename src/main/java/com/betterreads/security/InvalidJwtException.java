package com.betterreads.security;

import java.io.Serial;

class InvalidJwtException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    InvalidJwtException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
