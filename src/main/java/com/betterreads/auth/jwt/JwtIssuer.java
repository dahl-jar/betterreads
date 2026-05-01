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

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Issues and parses HS256-signed JWTs. The user id is stored as the {@code sub} claim.
 */
@Component
public final class JwtIssuer {

    private final SecretKey signingKey;

    private final String issuer;

    private final Duration expiration;

    /**
     * Spring-injected constructor. Resolves the signing key, issuer, and expiration from
     * {@link JwtProperties}.
     */
    @Autowired
    public JwtIssuer(final JwtProperties properties) {
        this(properties.secret(), properties.issuer(), Duration.ofMinutes(properties.expirationMinutes()));
    }

    /**
     * Direct constructor for tests that need to issue tokens with a custom secret, issuer, or
     * expiration (for example, an already-expired token to verify rejection).
     */
    public JwtIssuer(final String secret, final String issuer, final Duration expiration) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.expiration = expiration;
    }

    /**
     * Signs a token whose {@code sub} claim is the given user id.
     */
    public String issue(final long userId) {
        final Instant now = Instant.now();
        return Jwts.builder()
            .issuer(issuer)
            .subject(Long.toString(userId))
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(expiration)))
            .signWith(signingKey)
            .compact();
    }

    /**
     * Parses and validates a token, returning the user id from its {@code sub} claim.
     *
     * @throws InvalidJwtException if the token is malformed, has a bad signature, has expired,
     *     or carries a non-numeric subject
     */
    public long parseUserId(final String token) {
        try {
            final Jws<Claims> parsed = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token);
            return Long.parseLong(parsed.getPayload().getSubject());
        } catch (final JwtException | IllegalArgumentException ex) {
            throw new InvalidJwtException("Invalid JWT", ex);
        }
    }
}
