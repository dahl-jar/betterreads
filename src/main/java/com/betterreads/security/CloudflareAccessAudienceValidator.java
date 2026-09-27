package com.betterreads.security;

import java.util.List;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Cloudflare signs tokens for every app in the team with the same keys, so the {@code aud} claim
 * has to carry this app's audience tag.
 */
final class CloudflareAccessAudienceValidator implements OAuth2TokenValidator<Jwt> {

    private final String expectedAudience;

    CloudflareAccessAudienceValidator(final String expectedAudience) {
        this.expectedAudience = expectedAudience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(final Jwt token) {
        final List<String> audiences = token.getAudience();
        if (audiences != null && audiences.contains(expectedAudience)) {
            return OAuth2TokenValidatorResult.success();
        }
        final OAuth2Error error = new OAuth2Error(
            OAuth2ErrorCodes.INVALID_TOKEN,
            "JWT aud claim does not contain the expected Cloudflare Access audience",
            null
        );
        return OAuth2TokenValidatorResult.failure(error);
    }
}
