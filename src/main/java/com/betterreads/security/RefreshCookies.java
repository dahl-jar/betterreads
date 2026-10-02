package com.betterreads.security;

import java.time.Duration;
import java.time.Instant;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class RefreshCookies {

    public static final String COOKIE_NAME = "br_refresh";

    public static final String COOKIE_PATH = "/api/v1/auth";

    private static final Duration UNTIL_THE_BROWSER_CLOSES = Duration.ofSeconds(-1);

    private final RefreshCookieProperties cookieProperties;

    RefreshCookies(final RefreshCookieProperties cookieProperties) {
        this.cookieProperties = cookieProperties;
    }

    public ResponseCookie issue(final String value, final Instant expiresAt, final boolean persistent) {
        return cookie(value, persistent ? Duration.between(Instant.now(), expiresAt) : UNTIL_THE_BROWSER_CLOSES);
    }

    public ResponseCookie clear() {
        return cookie("", Duration.ZERO);
    }

    private ResponseCookie cookie(final String value, final Duration maxAge) {
        return ResponseCookie.from(COOKIE_NAME, value)
            .httpOnly(true)
            .secure(cookieProperties.secure())
            .sameSite(cookieProperties.sameSite())
            .path(COOKIE_PATH)
            .maxAge(maxAge)
            .build();
    }
}
