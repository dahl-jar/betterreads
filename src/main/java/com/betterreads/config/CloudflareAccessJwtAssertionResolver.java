package com.betterreads.config;

import jakarta.servlet.http.HttpServletRequest;

import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

/**
 * Reads the Cloudflare Access JWT from the {@code Cf-Access-Jwt-Assertion} header instead of
 * the default {@code Authorization: Bearer} header.
 *
 * <p>Cloudflare Access attaches the JWT in this custom header when forwarding authenticated
 * requests through a tunnel. The {@code Authorization} header is reserved for the app's own
 * JWT auth on other chains.
 */
public final class CloudflareAccessJwtAssertionResolver implements BearerTokenResolver {

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
