package com.betterreads.auth.cookie;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Refresh-cookie configuration bound from {@code auth.refresh-cookie.*}.
 *
 * @param secure when {@code true}, browsers send the cookie over HTTPS only
 */
@ConfigurationProperties(prefix = "auth.refresh-cookie")
public record RefreshCookieProperties(boolean secure) { }
