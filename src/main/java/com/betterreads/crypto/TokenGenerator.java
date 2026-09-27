package com.betterreads.crypto;

import java.security.SecureRandom;
import java.util.Base64;

/** URL-safe random tokens for email and password-reset links. */
public final class TokenGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TokenGenerator() {
    }

    public static String randomToken(final int byteLength) {
        final byte[] bytes = new byte[byteLength];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
