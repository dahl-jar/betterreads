package com.betterreads.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtIssuerTest {

    private static final String SECRET = "this-is-a-very-long-secret-for-hs256-tests-only-12345";

    private static final String OTHER_SECRET = "a-completely-different-secret-of-sufficient-length-67890";

    private static final String ISSUER = "betterreads-test";

    private static final long USER_ID = 42L;

    private static final Duration ONE_HOUR = Duration.ofHours(1);

    private static final Duration ALREADY_EXPIRED = Duration.ofSeconds(-1);

    private static final SecretKey SIGNING_KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    @Test
    void issuedTokenParsesBackToOriginalUserId() {
        final JwtIssuer issuer = new JwtIssuer(SECRET, ISSUER, ONE_HOUR);

        final String token = issuer.issue(USER_ID);

        assertThat(issuer.parseUserId(token)).isEqualTo(USER_ID);
    }

    @Test
    void issuedTokensCarryUniqueJti() {
        final JwtIssuer issuer = new JwtIssuer(SECRET, ISSUER, ONE_HOUR);

        final Claims firstClaims = readClaims(issuer.issue(USER_ID));
        final Claims secondClaims = readClaims(issuer.issue(USER_ID));

        assertThat(firstClaims.getId())
            .isNotBlank()
            .satisfies(UUID::fromString)
            .isNotEqualTo(secondClaims.getId());
    }

    @Test
    void tokenSignedWithOneSecretIsRejectedByDifferentSecret() {
        final JwtIssuer signer = new JwtIssuer(SECRET, ISSUER, ONE_HOUR);
        final JwtIssuer verifier = new JwtIssuer(OTHER_SECRET, ISSUER, ONE_HOUR);

        final String token = signer.issue(USER_ID);

        assertThatThrownBy(() -> verifier.parseUserId(token))
            .isInstanceOf(InvalidJwtException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        final JwtIssuer issuer = new JwtIssuer(SECRET, ISSUER, ALREADY_EXPIRED);

        final String token = issuer.issue(USER_ID);

        assertThatThrownBy(() -> issuer.parseUserId(token))
            .isInstanceOf(InvalidJwtException.class);
    }

    @Test
    void tokenWithWrongAudienceIsRejected() {
        final JwtIssuer issuer = new JwtIssuer(SECRET, ISSUER, ONE_HOUR);
        final String foreignToken = signedToken(ISSUER, "some-other-api");

        assertThatThrownBy(() -> issuer.parseUserId(foreignToken))
            .isInstanceOf(InvalidJwtException.class);
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        final JwtIssuer issuer = new JwtIssuer(SECRET, ISSUER, ONE_HOUR);
        final String foreignToken = signedToken("some-other-service", JwtIssuer.AUDIENCE);

        assertThatThrownBy(() -> issuer.parseUserId(foreignToken))
            .isInstanceOf(InvalidJwtException.class);
    }

    @Test
    void shouldRejectTokenWhenSubjectIsNotNumeric() {
        final JwtIssuer issuer = new JwtIssuer(SECRET, ISSUER, ONE_HOUR);
        final String token = signedToken(ISSUER, JwtIssuer.AUDIENCE, "darrow");

        assertThatThrownBy(() -> issuer.parseUserId(token))
            .isInstanceOf(InvalidJwtException.class);
    }

    private static String signedToken(final String issuerClaim, final String audienceClaim) {
        return signedToken(issuerClaim, audienceClaim, Long.toString(USER_ID));
    }

    private static String signedToken(final String issuerClaim, final String audienceClaim, final String subject) {
        return Jwts.builder()
            .issuer(issuerClaim)
            .audience().add(audienceClaim).and()
            .subject(subject)
            .signWith(SIGNING_KEY)
            .compact();
    }

    private static Claims readClaims(final String token) {
        return Jwts.parser()
            .verifyWith(SIGNING_KEY)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
