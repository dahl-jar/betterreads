package com.betterreads.security;

import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Refresh cookie settings bound from {@code auth.refresh-cookie.*}.
 *
 * <p>A split-origin deployment needs {@code sameSite=None}, and browsers drop a {@code None}
 * cookie that is not {@code Secure}, so that pair fails at startup.
 *
 * @param secure when {@code true}, browsers send the cookie over HTTPS only
 * @param sameSite the cookie's SameSite attribute: {@code Strict}, {@code Lax}, or {@code None}
 */
@ConfigurationProperties(prefix = "auth.refresh-cookie")
record RefreshCookieProperties(boolean secure, String sameSite) {

    private static final Set<String> SAME_SITE_NONE = Set.of("None", "none", "NONE");

    public RefreshCookieProperties {
        if (SAME_SITE_NONE.contains(sameSite) && !secure) {
            throw new IllegalArgumentException(
                "auth.refresh-cookie.same-site=None requires auth.refresh-cookie.secure=true");
        }
    }
}
