package com.betterreads.config;

import jakarta.servlet.http.HttpServletRequest;

import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

/**
 * Reads the Cloudflare Access JWT from {@code Cf-Access-Jwt-Assertion}.
 *
 * <p>The {@code Authorization} header is left alone so it remains available for the app's own
 * bearer token on the API chain.
 */
final class CloudflareAccessJwtAssertionResolver implements BearerTokenResolver {

    private static final String HEADER = "Cf-Access-Jwt-Assertion";

    @Override
    @Nullable
    public String resolve(final HttpServletRequest request) {
        final String value = request.getHeader(HEADER);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
