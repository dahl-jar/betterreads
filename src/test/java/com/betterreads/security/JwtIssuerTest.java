package com.betterreads.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtIssuerTest {

    private static final String SECRET = "this-is-a-very-long-secret-for-hs256-tests-only-12345";

    private static final String OTHER_SECRET = "a-completely-different-secret-of-sufficient-length-67890";

    private static final String ISSUER = "betterreads-test";

    private static final long USER_ID = 42L;

    private static final int CREDENTIAL_VERSION = 3;

    private static final Duration ONE_HOUR = Duration.ofHours(1);

    private static final Duration ALREADY_EXPIRED = Duration.ofSeconds(-1);

    private static final SecretKey SIGNING_KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    @Test
    void shouldParseAnIssuedTokenBackToItsUserAndCredentialVersion() {
        final JwtIssuer issuer = new JwtIssuer(SECRET, ISSUER, ONE_HOUR);
        final String token = issuer.issue(USER_ID, CREDENTIAL_VERSION);

        final AccessToken parsed = issuer.parse(token);

        assertThat(parsed.userId()).isEqualTo(USER_ID);
        assertThat(parsed.credentialVersion()).isEqualTo(CREDENTIAL_VERSION);
    }

    @Test
    void issuedTokensCarryUniqueJti() {
        final JwtIssuer issuer = new JwtIssuer(SECRET, ISSUER, ONE_HOUR);

        final Claims firstClaims = readClaims(issuer.issue(USER_ID, CREDENTIAL_VERSION));
        final Claims secondClaims = readClaims(issuer.issue(USER_ID, CREDENTIAL_VERSION));

        assertThat(firstClaims.getId())
            .isNotBlank()
            .satisfies(UUID::fromString)
            .isNotEqualTo(secondClaims.getId());
    }

    @Test
    void tokenSignedWithOneSecretIsRejectedByDifferentSecret() {
        final JwtIssuer signer = new JwtIssuer(SECRET, ISSUER, ONE_HOUR);
        final JwtIssuer verifier = new JwtIssuer(OTHER_SECRET, ISSUER, ONE_HOUR);

        final String token = signer.issue(USER_ID, CREDENTIAL_VERSION);

        assertThatThrownBy(() -> verifier.parse(token))
            .isInstanceOf(InvalidJwtException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        final JwtIssuer issuer = new JwtIssuer(SECRET, ISSUER, ALREADY_EXPIRED);

        final String token = issuer.issue(USER_ID, CREDENTIAL_VERSION);

        assertThatThrownBy(() -> issuer.parse(token))
            .isInstanceOf(InvalidJwtException.class);
    }

    @Test
    void tokenWithWrongAudienceIsRejected() {
        final JwtIssuer issuer = new JwtIssuer(SECRET, ISSUER, ONE_HOUR);
        final String foreignToken = signedToken(ISSUER, "some-other-api");

        assertThatThrownBy(() -> issuer.parse(foreignToken))
            .isInstanceOf(InvalidJwtException.class);
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        final JwtIssuer issuer = new JwtIssuer(SECRET, ISSUER, ONE_HOUR);
        final String foreignToken = signedToken("some-other-service", JwtIssuer.AUDIENCE);

        assertThatThrownBy(() -> issuer.parse(foreignToken))
            .isInstanceOf(InvalidJwtException.class);
    }

    @Test
    void shouldRejectTokenWhenSubjectIsNotNumeric() {
        final JwtIssuer issuer = new JwtIssuer(SECRET, ISSUER, ONE_HOUR);
        final String token = signedToken(ISSUER, JwtIssuer.AUDIENCE, "user", CREDENTIAL_VERSION);

        assertThatThrownBy(() -> issuer.parse(token))
            .isInstanceOf(InvalidJwtException.class);
    }

    @Test
    void shouldRejectATokenWithoutACredentialVersion() {
        final JwtIssuer issuer = new JwtIssuer(SECRET, ISSUER, ONE_HOUR);
        final String token = signedToken(ISSUER, JwtIssuer.AUDIENCE, Long.toString(USER_ID), null);

        assertThatThrownBy(() -> issuer.parse(token))
            .isInstanceOf(InvalidJwtException.class);
    }

    private static String signedToken(final String issuerClaim, final String audienceClaim) {
        return signedToken(issuerClaim, audienceClaim, Long.toString(USER_ID), CREDENTIAL_VERSION);
    }

    private static String signedToken(final String issuerClaim, final String audienceClaim, final String subject,
        final @Nullable Integer credentialVersion) {
        return Jwts.builder()
            .issuer(issuerClaim)
            .audience().add(audienceClaim).and()
            .subject(subject)
            .claim(JwtIssuer.CREDENTIAL_VERSION_CLAIM, credentialVersion)
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
