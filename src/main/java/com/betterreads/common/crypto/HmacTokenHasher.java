package com.betterreads.common.crypto;

import com.betterreads.auth.jwt.JwtProperties;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * HMAC-SHA256 hasher for keyed tokens stored in the auth domain (refresh, password reset,
 * email verification). Keyed by the JWT secret so a DB-only leak cannot reconstruct active
 * tokens. HMAC is used (not BCrypt) because these tokens are 256-bit random and run on every
 * lookup; the password-style brute-force angle does not apply.
 *
 * <p>One shared bean replaces three previously-duplicate per-domain hashers. The hash contract
 * is identical for every domain; lifetime, revocation, and consume rules live in the services
 * that call this class.
 */
@Component
public final class HmacTokenHasher {

    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] secret;

    @Autowired
    public HmacTokenHasher(final JwtProperties properties) {
        this(properties.secret());
    }

    /**
     * Test-friendly constructor that takes a raw secret instead of {@link JwtProperties}.
     */
    public HmacTokenHasher(final String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8).clone();
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
