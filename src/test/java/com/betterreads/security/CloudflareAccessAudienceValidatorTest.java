package com.betterreads.security;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;

/** Cloudflare sends {@code aud} as an array that can hold several tags, so a match is containment. */
@DisplayName("CloudflareAccessAudienceValidator")
final class CloudflareAccessAudienceValidatorTest {

    private static final String EXPECTED_AUD = "expected-aud-tag";

    private static final String OTHER_AUD = "some-other-aud";

    private static final long ONE_MINUTE_SECONDS = 60L;

    private static final String INVALID_TOKEN = "invalid_token";

    @Nested
    @DisplayName("validate")
    class Validate {

        @Test
        void returnsSuccessWhenAudListIncludesExpected() {
            final CloudflareAccessAudienceValidator validator =
                new CloudflareAccessAudienceValidator(EXPECTED_AUD);
            final Jwt jwt = jwtWithAudience(List.of(OTHER_AUD, EXPECTED_AUD));

            final OAuth2TokenValidatorResult result = validator.validate(jwt);

            assertThat(result.hasErrors()).isFalse();
        }

        @Test
        void shouldReturnErrorWhenAudClaimIsMissing() {
            final CloudflareAccessAudienceValidator validator =
                new CloudflareAccessAudienceValidator(EXPECTED_AUD);
            final Jwt jwt = unsignedJwt().build();

            final OAuth2TokenValidatorResult result = validator.validate(jwt);

            assertThat(result.getErrors())
                .anySatisfy(err ->
                    assertThat(err.getErrorCode()).isEqualTo(INVALID_TOKEN));
        }
    }

    private static Jwt jwtWithAudience(final List<String> audiences) {
        return unsignedJwt()
            .claim("aud", audiences)
            .build();
    }

    private static Jwt.Builder unsignedJwt() {
        return Jwt.withTokenValue("test-token")
            .header("alg", "RS256")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(ONE_MINUTE_SECONDS));
    }
}
