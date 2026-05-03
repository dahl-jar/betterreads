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
 * HMAC-SHA256 hasher for refresh tokens. Keys off the JWT secret so a database read leak
 * cannot let an attacker reconstruct issued tokens without also having the JWT secret.
 *
 * <p>Refresh tokens are 256-bit random opaque strings; there is no password-style brute-force
 * angle. HMAC is sufficient and orders of magnitude faster than BCrypt, which matters because
 * hashing happens on every refresh request.
 */
@Component
public final class RefreshTokenHasher {

    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] secret;

    /**
     * Spring-injected constructor that derives the HMAC key from {@link JwtProperties#secret()}.
     */
    @Autowired
    public RefreshTokenHasher(final JwtProperties properties) {
        this(properties.secret());
    }

    /**
     * Direct constructor for tests that do not bring up the Spring context.
     */
    public RefreshTokenHasher(final String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8).clone();
    }

    /**
     * Returns the lowercase hex HMAC-SHA256 of the given token. Deterministic for the same
     * input and key.
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
