package com.betterreads.operations.access;

import java.util.List;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Verifies the {@code aud} claim contains the expected Cloudflare Access Application Audience
 * tag. Without this check, any JWT signed by Cloudflare for any application in the same team
 * would pass signature validation.
 */
public final class CloudflareAccessAudienceValidator implements OAuth2TokenValidator<Jwt> {

    private final String expectedAudience;

    public CloudflareAccessAudienceValidator(final String expectedAudience) {
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
