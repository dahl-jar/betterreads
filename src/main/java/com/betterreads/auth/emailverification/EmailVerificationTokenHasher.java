package com.betterreads.auth.emailverification;

import com.betterreads.auth.jwt.JwtProperties;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

/**
 * HMAC-SHA256 hasher for email-verification tokens. Keyed by the JWT secret so a DB-only leak
 * cannot reconstruct active tokens. Kept separate from the password-reset hasher because the
 * two domains have different lifetimes and consume rules; collapsing them now would force one
 * class to grow conditionals as soon as either side changes.
 */
@Component
final class EmailVerificationTokenHasher {

    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] secret;

    EmailVerificationTokenHasher(final JwtProperties properties) {
        this.secret = properties.secret().getBytes(StandardCharsets.UTF_8).clone();
    }

    /**
     * Returns the lowercase hex HMAC-SHA256 of {@code token}.
     */
    public String hash(final String token) {
        try {
            final Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            final byte[] digest = mac.doFinal(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (final NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("HmacSHA256 not available", ex);
        }
    }
}
