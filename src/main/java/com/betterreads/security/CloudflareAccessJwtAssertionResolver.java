package com.betterreads.security;

import jakarta.servlet.http.HttpServletRequest;

import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.util.StringUtils;

/**
 * Reads the Cloudflare Access JWT from {@code Cf-Access-Jwt-Assertion}, so {@code Authorization}
 * stays free for the app's own bearer token.
 */
final class CloudflareAccessJwtAssertionResolver implements BearerTokenResolver {

    private static final String HEADER = "Cf-Access-Jwt-Assertion";

    @Override
    @Nullable
    public String resolve(final HttpServletRequest request) {
        final String value = request.getHeader(HEADER);
        return StringUtils.hasText(value) ? value : null;
    }
}
