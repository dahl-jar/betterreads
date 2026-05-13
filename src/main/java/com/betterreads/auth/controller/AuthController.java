package com.betterreads.auth.controller;

import com.betterreads.auth.cookie.RefreshCookieProperties;
import com.betterreads.auth.deletion.AccountDeletionService;
import com.betterreads.auth.dto.AuthResponse;
import com.betterreads.auth.dto.ForgotPasswordRequest;
import com.betterreads.auth.dto.LoginRequest;
import com.betterreads.auth.dto.RegisterRequest;
import com.betterreads.auth.dto.ResendVerificationRequest;
import com.betterreads.auth.dto.ResetPasswordRequest;
import com.betterreads.auth.dto.UserResponse;
import com.betterreads.auth.dto.VerifyEmailRequest;
import com.betterreads.auth.emailverification.EmailVerificationService;
import com.betterreads.auth.jwt.JwtProperties;
import com.betterreads.auth.passwordreset.PasswordResetService;
import com.betterreads.auth.service.AuthService;
import com.betterreads.auth.service.TokenPair;
import com.betterreads.common.dto.ApiErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
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
import org.springframework.web.bind.annotation.DeleteMapping;
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

    private final PasswordResetService passwordResetService;

    private final EmailVerificationService emailVerificationService;

    private final AccountDeletionService accountDeletionService;

    private final RefreshCookieProperties cookieProperties;

    private final Duration refreshLifetime;

    @SuppressWarnings("PMD.ExcessiveParameterList")
    AuthController(
        final AuthService authService,
        final PasswordResetService passwordResetService,
        final EmailVerificationService emailVerificationService,
        final AccountDeletionService accountDeletionService,
        final RefreshCookieProperties cookieProperties,
        final JwtProperties jwtProperties
    ) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
        this.emailVerificationService = emailVerificationService;
        this.accountDeletionService = accountDeletionService;
        this.cookieProperties = cookieProperties;
        this.refreshLifetime = Duration.ofDays(jwtProperties.refreshExpirationDays());
    }

    /**
     * Creates a new user account, issues an access JWT, and writes the refresh token to the
     * {@value #COOKIE_NAME} cookie.
     */
    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    @SecurityRequirements
    @ApiResponse(responseCode = "201", description = "Account created")
    @ApiResponse(responseCode = "400", description = "Validation failed",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Username or email already taken",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "429", description = "Rate limited",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
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
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "Authenticated")
    @ApiResponse(responseCode = "400", description = "Validation failed",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Invalid credentials",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "429", description = "Rate limited",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
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
    @ApiResponse(responseCode = "200", description = "Current user profile")
    @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public UserResponse me(@AuthenticationPrincipal final Long userId) {
        return authService.currentUser(userId);
    }

    /**
     * Soft-deletes the authenticated user. The row stays in {@code app_user} during a 30-day
     * grace window, then a scheduled sweep hard-deletes it. Refresh tokens are revoked
     * immediately, outstanding password-reset and verification tokens are invalidated, and the
     * cookie is cleared from the browser.
     *
     * <p>Idempotent: returns {@code 204} whether the account was active or already soft-deleted.
     * The access JWT remains valid until natural expiry; refresh-revoke kills the renewal path.
     */
    @DeleteMapping("/me")
    @Operation(summary = "Delete the current account (soft-delete with 30-day grace)")
    @ApiResponse(responseCode = "204", description = "Account soft-deleted")
    @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<Void> deleteMe(@AuthenticationPrincipal final Long userId) {
        accountDeletionService.deleteOwnAccount(userId);
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, clearCookie().toString())
            .build();
    }

    /**
     * Rotates the refresh token presented in the {@value #COOKIE_NAME} cookie and returns a
     * fresh access JWT. The successor refresh token replaces the cookie value.
     *
     * <p>Authenticated solely by the refresh token cookie. A missing cookie is rejected with 401.
     */
    @PostMapping("/refresh")
    @Operation(summary = "Rotate access and refresh tokens")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "New access token; refresh cookie rotated")
    @ApiResponse(responseCode = "401", description = "Missing, expired, or already-rotated refresh token",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
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
    @SecurityRequirements
    @ApiResponse(responseCode = "204", description = "Refresh cookie cleared")
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

    /**
     * Issues a password-reset token and sends it to the supplied email when an account matches.
     * Returns {@code 204} regardless so the response cannot be used to enumerate registered
     * addresses.
     */
    @PostMapping("/forgot-password")
    @Operation(summary = "Start a password reset")
    @SecurityRequirements
    @ApiResponse(responseCode = "204", description = "Reset email dispatched if account exists")
    @ApiResponse(responseCode = "400", description = "Validation failed",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "429", description = "Rate limited",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody final ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.email());
        return ResponseEntity.noContent().build();
    }

    /**
     * Consumes a reset token, replaces the user's password, and revokes every refresh token for
     * the account so other devices are signed out. Returns {@code 400} for unknown, expired, or
     * already-consumed tokens with the same message in each branch.
     */
    @PostMapping("/reset-password")
    @Operation(summary = "Complete a password reset")
    @SecurityRequirements
    @ApiResponse(responseCode = "204", description = "Password replaced")
    @ApiResponse(responseCode = "400", description = "Invalid, expired, or already-consumed token",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "429", description = "Rate limited",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody final ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    /**
     * Confirms ownership of an email address by consuming the verification token sent at
     * registration. Returns {@code 204} for successful and replayed-success cases (idempotent);
     * returns {@code 400} for unknown or expired tokens.
     */
    @PostMapping("/verify-email")
    @Operation(summary = "Confirm an email address")
    @SecurityRequirements
    @ApiResponse(responseCode = "204", description = "Email verified or replay accepted")
    @ApiResponse(responseCode = "400", description = "Invalid or expired token",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "429", description = "Rate limited",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody final VerifyEmailRequest request) {
        emailVerificationService.verify(request.token());
        return ResponseEntity.noContent().build();
    }

    /**
     * Issues a fresh verification link for the supplied email when an unverified account
     * matches. Returns {@code 204} for unknown addresses and for already-verified accounts so
     * the response cannot be used to enumerate registered or unverified emails.
     */
    @PostMapping("/resend-verification")
    @Operation(summary = "Resend an email-verification link")
    @SecurityRequirements
    @ApiResponse(responseCode = "204", description = "Resend email dispatched if account is unverified")
    @ApiResponse(responseCode = "400", description = "Validation failed",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "429", description = "Rate limited",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<Void> resendVerification(
        @Valid @RequestBody final ResendVerificationRequest request
    ) {
        emailVerificationService.requestResend(request.email());
        return ResponseEntity.noContent().build();
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
