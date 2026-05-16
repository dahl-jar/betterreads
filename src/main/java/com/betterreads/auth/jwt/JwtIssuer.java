package com.betterreads.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Issues and parses HS256-signed JWTs. The subject claim carries the user id; each token also
 * has an {@code aud} and a per-token {@code jti}.
 */
@Component
public final class JwtIssuer {

    static final String AUDIENCE = "betterreads-api";

    private final SecretKey signingKey;

    private final String issuer;

    private final Duration expiration;

    @Autowired
    public JwtIssuer(final JwtProperties properties) {
        this(properties.secret(), properties.issuer(), Duration.ofMinutes(properties.expirationMinutes()));
    }

    /**
     * Constructor for direct wiring with a raw secret, issuer, and lifetime, without going
     * through {@link JwtProperties}.
     */
    public JwtIssuer(final String secret, final String issuer, final Duration expiration) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.expiration = expiration;
    }

    /** Returns a signed access JWT for the user. */
    public String issue(final long userId) {
        final Instant now = Instant.now();
        return Jwts.builder()
            .issuer(issuer)
            .audience().add(AUDIENCE).and()
            .id(UUID.randomUUID().toString())
            .subject(Long.toString(userId))
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(expiration)))
            .signWith(signingKey)
            .compact();
    }

    /**
     * Returns the user id from the subject claim, or throws if the token does not pass every
     * check.
     *
     * <p>Audience is checked so a token minted for another service cannot be replayed here.
     *
     * @throws InvalidJwtException malformed, bad signature, expired, wrong issuer, wrong
     *         audience, or non-numeric subject
     */
    public long parseUserId(final String token) {
        try {
            final Jws<Claims> parsed = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .requireAudience(AUDIENCE)
                .build()
                .parseSignedClaims(token);
            return Long.parseLong(parsed.getPayload().getSubject());
        } catch (final JwtException | IllegalArgumentException ex) {
            throw new InvalidJwtException("Invalid JWT", ex);
        }
    }
}
