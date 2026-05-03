package com.betterreads.auth.refresh;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.betterreads.auth.jwt.JwtProperties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * HMAC-SHA256 hasher for refresh tokens. Keyed by the JWT secret so a DB-only leak cannot
 * reconstruct active tokens. HMAC is used (not BCrypt) because refresh tokens are 256-bit
 * random and run on every refresh; the password-style brute-force angle does not apply.
 */
@Component
public final class RefreshTokenHasher {

    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] secret;

    @Autowired
    public RefreshTokenHasher(final JwtProperties properties) {
        this(properties.secret());
    }

    /**
     * Test-friendly constructor that takes a raw secret instead of {@link JwtProperties}.
     */
    public RefreshTokenHasher(final String secret) {
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
