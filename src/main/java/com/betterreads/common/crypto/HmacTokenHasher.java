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
 * HMAC-SHA256 hasher for refresh, password-reset, and email-verification tokens.
 *
 * <p>Keyed by the JWT secret so a DB leak alone cannot reconstruct active tokens. HMAC is
 * used instead of BCrypt because these tokens are 256-bit random and run on every lookup;
 * BCrypt's slow design exists to fight password-style brute-force, which does not apply.
 */
@Component
public final class HmacTokenHasher {

    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] secret;

    @Autowired
    public HmacTokenHasher(final JwtProperties properties) {
        this(properties.secret());
    }

    /** Constructor for direct wiring with a raw secret, without {@link JwtProperties}. */
    public HmacTokenHasher(final String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8).clone();
    }

    /** Returns the lowercase hex HMAC-SHA256 of {@code token}. */
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
