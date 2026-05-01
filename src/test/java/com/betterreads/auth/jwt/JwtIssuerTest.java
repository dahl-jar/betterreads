package com.betterreads.auth.jwt;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtIssuerTest {

    private static final String SECRET = "this-is-a-very-long-secret-for-hs256-tests-only-12345";

    private static final String OTHER_SECRET = "a-completely-different-secret-of-sufficient-length-67890";

    private static final String ISSUER = "betterreads-test";

    private static final long USER_ID = 42L;

    private static final Duration ONE_HOUR = Duration.ofHours(1);

    private static final Duration ALREADY_EXPIRED = Duration.ofSeconds(-1);

    @Test
    void issuedTokenParsesBackToOriginalUserId() {
        final JwtIssuer issuer = new JwtIssuer(SECRET, ISSUER, ONE_HOUR);

        final String token = issuer.issue(USER_ID);

        assertThat(issuer.parseUserId(token)).isEqualTo(USER_ID);
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
    void malformedTokenIsRejected() {
        final JwtIssuer issuer = new JwtIssuer(SECRET, ISSUER, ONE_HOUR);

        assertThatThrownBy(() -> issuer.parseUserId("not.a.real.jwt"))
            .isInstanceOf(InvalidJwtException.class);
    }
}
