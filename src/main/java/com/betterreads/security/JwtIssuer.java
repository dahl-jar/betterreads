package com.betterreads.security;

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
 * Issues and parses HS256 access tokens. The subject carries the user id, and every token gets an
 * {@code aud} and its own {@code jti}.
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

    public JwtIssuer(final String secret, final String issuer, final Duration expiration) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.expiration = expiration;
    }

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
     * The audience check rejects a token issued for another service.
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
            throw new InvalidJwtException("Invalid JWT: " + ex.getClass().getSimpleName(), ex);
        }
    }
}
