package com.betterreads.auth.cookie;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Refresh-token cookie configuration bound from {@code auth.refresh-cookie.*}. Only the
 * {@code secure} flag varies per environment; production stays {@code true}, local HTTP dev
 * sets it {@code false} so browsers still accept the cookie. Cookie name and path are
 * controller-level constants because they must match between issue and read paths.
 *
 * @param secure when {@code true}, browsers send the cookie over HTTPS only
 */
@ConfigurationProperties(prefix = "auth.refresh-cookie")
public record RefreshCookieProperties(boolean secure) { }
