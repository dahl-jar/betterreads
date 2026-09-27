package com.betterreads.crypto;

import com.betterreads.errors.InvalidRequestException;

import java.nio.charset.StandardCharsets;

/**
 * Enforces BCrypt's 72-byte input limit.
 *
 * <p>Bean Validation's {@code @Size(max = 72)} on the DTO counts Java characters, but BCrypt
 * counts UTF-8 bytes. A 20-character password made of four-byte code points is 80 bytes and
 * would be silently truncated, leaving the user's effective password shorter than they think.
 */
public final class PasswordByteLimit {

    private static final int MAX_BYTES = 72;

    private static final String MESSAGE = "Password exceeds " + MAX_BYTES + "-byte limit";

    private PasswordByteLimit() {
    }

    public static void check(final String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new InvalidRequestException(MESSAGE);
        }
    }
}
