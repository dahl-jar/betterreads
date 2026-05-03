package com.betterreads.operations.access;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for the Cloudflare Access AUD claim validator. The validator runs as part of
 * the JWT decoder pipeline; if AUD doesn't match, validation must fail.
 *
 * <p>Cloudflare emits {@code aud} as a JSON array, so {@code returnsSuccessWhenAudListIncludesExpected}
 * exists to catch a likely implementation mistake of using {@code equals} instead of {@code contains}.
 */
@DisplayName("CloudflareAccessAudienceValidator")
final class CloudflareAccessAudienceValidatorTest {

    private static final String EXPECTED_AUD = "expected-aud-tag";

    private static final String OTHER_AUD = "some-other-aud";

    private static final long ONE_MINUTE_SECONDS = 60L;

    @Nested
    @DisplayName("validate")
    class Validate {

        @Test
        void returnsSuccessWhenAudMatches() {
            final CloudflareAccessAudienceValidator validator =
                new CloudflareAccessAudienceValidator(EXPECTED_AUD);
            final Jwt jwt = jwtWithAudience(List.of(EXPECTED_AUD));

            final OAuth2TokenValidatorResult result = validator.validate(jwt);

            assertThat(result.hasErrors()).isFalse();
        }

        @Test
        void returnsSuccessWhenAudListIncludesExpected() {
            final CloudflareAccessAudienceValidator validator =
                new CloudflareAccessAudienceValidator(EXPECTED_AUD);
            final Jwt jwt = jwtWithAudience(List.of(OTHER_AUD, EXPECTED_AUD));

            final OAuth2TokenValidatorResult result = validator.validate(jwt);

            assertThat(result.hasErrors()).isFalse();
        }

        @Test
        void returnsErrorWhenAudDoesNotMatch() {
            final CloudflareAccessAudienceValidator validator =
                new CloudflareAccessAudienceValidator(EXPECTED_AUD);
            final Jwt jwt = jwtWithAudience(List.of(OTHER_AUD));

            final OAuth2TokenValidatorResult result = validator.validate(jwt);

            assertThat(result.getErrors())
                .isNotEmpty()
                .anySatisfy(err ->
                    assertThat(err.getErrorCode()).isEqualTo("invalid_token"));
        }
    }

    private static Jwt jwtWithAudience(final List<String> audiences) {
        return Jwt.withTokenValue("test-token")
            .header("alg", "RS256")
            .claim("aud", audiences)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(ONE_MINUTE_SECONDS))
            .build();
    }
}
