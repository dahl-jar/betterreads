package com.betterreads.auth.token;

import java.security.SecureRandom;
import java.util.Base64;

/** Generates URL-safe random plaintext tokens for single-use email and reset tokens. */
public final class TokenGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TokenGenerator() {
    }

    /** Returns a URL-safe Base64 token built from {@code byteLength} random bytes. */
    public static String randomToken(final int byteLength) {
        final byte[] bytes = new byte[byteLength];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
