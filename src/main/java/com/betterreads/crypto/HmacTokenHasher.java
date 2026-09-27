package com.betterreads.crypto;

import com.betterreads.security.JwtProperties;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

/** Hashes stored tokens keyed by the JWT secret, so a DB leak alone cannot reconstruct active tokens. */
@Component
public final class HmacTokenHasher {

    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] secret;

    public HmacTokenHasher(final JwtProperties properties) {
        this.secret = properties.secret().getBytes(StandardCharsets.UTF_8);
    }

    /** Returns the lowercase hex HMAC-SHA256 of {@code token}. */
    public String hash(final String token) {
        try {
            final Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            final byte[] digest = mac.doFinal(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (final NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("HmacSHA256 unavailable or key rejected", ex);
        }
    }
}
