package com.betterreads.auth.controller;

import com.betterreads.auth.cookie.RefreshCookieProperties;
import com.betterreads.auth.dto.AuthResponse;
import com.betterreads.auth.dto.LoginRequest;
import com.betterreads.auth.dto.RegisterRequest;
import com.betterreads.auth.dto.UserResponse;
import com.betterreads.auth.jwt.JwtProperties;
import com.betterreads.auth.service.AuthService;
import com.betterreads.auth.service.TokenPair;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints. {@code register}, {@code login}, {@code refresh}, and {@code logout}
 * are public; {@code me} requires a valid access JWT. The refresh token is carried in the
 * {@value #COOKIE_NAME} {@code HttpOnly} cookie scoped to {@value #COOKIE_PATH} so JavaScript
 * on the frontend cannot read it and {@code SameSite=Strict} blocks cross-site POSTs from
 * carrying it.
 */
@RestController
@RequestMapping(AuthController.COOKIE_PATH)
@Tag(name = "Authentication", description = "Register, log in, and look up the current user")
class AuthController {

    static final String COOKIE_NAME = "br_refresh";

    static final String COOKIE_PATH = "/api/v1/auth";

    private static final String SAME_SITE_STRICT = "Strict";

    private final AuthService authService;

    private final RefreshCookieProperties cookieProperties;

    private final Duration refreshLifetime;

    AuthController(
        final AuthService authService,
        final RefreshCookieProperties cookieProperties,
        final JwtProperties jwtProperties
    ) {
        this.authService = authService;
        this.cookieProperties = cookieProperties;
        this.refreshLifetime = Duration.ofDays(jwtProperties.refreshExpirationDays());
    }

    /**
     * Creates a new user account, issues an access JWT, and writes the refresh token to the
     * {@value #COOKIE_NAME} cookie.
     */
    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody final RegisterRequest request) {
        final TokenPair pair = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .header(HttpHeaders.SET_COOKIE, issueCookie(pair.refreshToken()).toString())
            .body(pair.body());
    }

    /**
     * Authenticates the credentials, issues an access JWT, and writes the refresh token to the
     * {@value #COOKIE_NAME} cookie. The identifier is matched against username first, then email.
     */
    @PostMapping("/login")
    @Operation(summary = "Log in with username or email")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody final LoginRequest request) {
        final TokenPair pair = authService.login(request);
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, issueCookie(pair.refreshToken()).toString())
            .body(pair.body());
    }

    /**
     * Returns the profile of the currently authenticated user.
     */
    @GetMapping("/me")
    @Operation(summary = "Get the current authenticated user")
    public UserResponse me(@AuthenticationPrincipal final Long userId) {
        return authService.currentUser(userId);
    }

    /**
     * Rotates the refresh token presented in the {@value #COOKIE_NAME} cookie and returns a
     * fresh access JWT. The successor refresh token replaces the cookie value.
     *
     * <p>Authenticated solely by the refresh token cookie. A missing cookie is rejected with 401.
     */
    @PostMapping("/refresh")
    @Operation(summary = "Rotate access and refresh tokens")
    public ResponseEntity<AuthResponse> refresh(
        @CookieValue(name = COOKIE_NAME, required = false) final String refreshToken
    ) {
        if (refreshToken == null) {
            throw new BadCredentialsException("Missing refresh token");
        }
        final TokenPair pair = authService.refresh(refreshToken);
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, issueCookie(pair.refreshToken()).toString())
            .body(pair.body());
    }

    /**
     * Revokes the refresh token presented in the {@value #COOKIE_NAME} cookie and clears the
     * cookie from the browser.
     *
     * <p>Idempotent: returns 204 whether or not the cookie was present or the token was already
     * revoked.
     */
    @PostMapping("/logout")
    @Operation(summary = "Revoke a refresh token")
    public ResponseEntity<Void> logout(
        @CookieValue(name = COOKIE_NAME, required = false) final String refreshToken
    ) {
        if (refreshToken != null) {
            authService.logout(refreshToken);
        }
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, clearCookie().toString())
            .build();
    }

    private ResponseCookie issueCookie(final String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
            .httpOnly(true)
            .secure(cookieProperties.secure())
            .sameSite(SAME_SITE_STRICT)
            .path(COOKIE_PATH)
            .maxAge(refreshLifetime)
            .build();
    }

    private ResponseCookie clearCookie() {
        return ResponseCookie.from(COOKIE_NAME, "")
            .httpOnly(true)
            .secure(cookieProperties.secure())
            .sameSite(SAME_SITE_STRICT)
            .path(COOKIE_PATH)
            .maxAge(0)
            .build();
    }
}
