package com.betterreads.common.crypto;

import com.betterreads.common.exception.InvalidRequestException;

import java.nio.charset.StandardCharsets;

/**
 * Enforces BCrypt's 72-byte input limit. Bean Validation's {@code @Size(max = 72)} on the
 * password DTO field counts Java characters; BCrypt counts UTF-8 bytes. A password of 20
 * four-byte code points (80 bytes) passes character validation and is then silently
 * truncated, which means later bytes do not contribute to the hash and the user's perceived
 * password is shorter than they think.
 *
 * <p>Called at the service layer immediately before {@code passwordEncoder.encode}. Throws
 * {@link InvalidRequestException} so the global handler maps it to a 400 with the same shape
 * as a malformed request body.
 */
public final class PasswordByteLimit {

    static final int MAX_BYTES = 72;

    private static final String MESSAGE = "Password exceeds " + MAX_BYTES + "-byte limit";

    private PasswordByteLimit() {
    }

    /**
     * Throws {@link InvalidRequestException} if {@code password} encodes to more than 72 UTF-8
     * bytes. ASCII passwords up to 72 characters are always within the limit.
     */
    public static void check(final String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new InvalidRequestException(MESSAGE);
        }
    }
}
