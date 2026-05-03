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
 * Issues and parses HS256-signed JWTs. Subject claim carries the user id.
 */
@Component
public final class JwtIssuer {

    private final SecretKey signingKey;

    private final String issuer;

    private final Duration expiration;

    @Autowired
    public JwtIssuer(final JwtProperties properties) {
        this(properties.secret(), properties.issuer(), Duration.ofMinutes(properties.expirationMinutes()));
    }

    /**
     * Test-friendly constructor that takes a raw secret, issuer, and lifetime so tests can
     * issue already-expired or wrong-issuer tokens without bringing up a Spring context.
     */
    public JwtIssuer(final String secret, final String issuer, final Duration expiration) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.expiration = expiration;
    }

    /**
     * Returns a signed access JWT whose subject is the given user id.
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
     * Returns the user id parsed from the subject claim.
     *
     * @throws InvalidJwtException malformed, bad signature, expired, or non-numeric subject
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
