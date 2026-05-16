package com.betterreads.operations.access;

import java.util.List;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Checks that the JWT {@code aud} claim matches the expected Cloudflare Access Application
 * Audience tag.
 *
 * <p>Without this check, any JWT signed by Cloudflare for any app in the same team would pass
 * signature checks against this service.
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
