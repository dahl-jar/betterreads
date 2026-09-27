package com.betterreads.security;

import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class RefreshCookies {

    public static final String COOKIE_NAME = "br_refresh";

    public static final String COOKIE_PATH = "/api/v1/auth";

    private final RefreshCookieProperties cookieProperties;

    private final Duration refreshLifetime;

    RefreshCookies(final RefreshCookieProperties cookieProperties, final JwtProperties jwtProperties) {
        this.cookieProperties = cookieProperties;
        this.refreshLifetime = Duration.ofDays(jwtProperties.refreshExpirationDays());
    }

    public ResponseCookie issue(final String value) {
        return cookie(value, refreshLifetime);
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
