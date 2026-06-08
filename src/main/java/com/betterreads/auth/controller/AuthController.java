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
import org.springframework.http.ProblemDetail;

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
 * Authentication endpoints. {@code me} requires a valid access JWT; the rest are public.
 *
 * <p>The refresh token is sent in the {@value #COOKIE_NAME} {@code HttpOnly} cookie so frontend
 * JavaScript cannot read it. The cookie's SameSite and Secure attributes are configured per
 * environment so a same-origin deployment can use {@code Strict} while a split apex/API deployment
 * uses {@code None} with {@code Secure}.
 */
@RestController
@RequestMapping(AuthController.COOKIE_PATH)
@Tag(name = "Authentication", description = "Register, log in, and look up the current user")
class AuthController {

    static final String COOKIE_NAME = "br_refresh";

    static final String COOKIE_PATH = "/api/v1/auth";

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
            final JwtProperties jwtProperties) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
        this.emailVerificationService = emailVerificationService;
        this.accountDeletionService = accountDeletionService;
        this.cookieProperties = cookieProperties;
        this.refreshLifetime = Duration.ofDays(jwtProperties.refreshExpirationDays());
    }

    /** Creates a new user account and returns an access JWT plus refresh cookie. */
    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    @SecurityRequirements
    @ApiResponse(responseCode = "201", description = "Account created")
    @ApiResponse(responseCode = "400", description = "Validation failed",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Username or email already taken",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "429", description = "Rate limited",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody final RegisterRequest request) {
        final TokenPair pair = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .header(HttpHeaders.SET_COOKIE, issueCookie(pair.refreshToken()).toString())
            .body(pair.body());
    }

    /** Authenticates the credentials and returns an access JWT plus refresh cookie. */
    @PostMapping("/login")
    @Operation(summary = "Log in with username or email")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "Authenticated")
    @ApiResponse(responseCode = "400", description = "Validation failed",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Invalid credentials",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "429", description = "Rate limited",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody final LoginRequest request) {
        final TokenPair pair = authService.login(request);
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, issueCookie(pair.refreshToken()).toString())
            .body(pair.body());
    }

    /** Returns the profile of the authenticated user. */
    @GetMapping("/me")
    @Operation(summary = "Get the current authenticated user")
    @ApiResponse(responseCode = "200", description = "Current user profile")
    @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public UserResponse me(@AuthenticationPrincipal final Long userId) {
        return authService.currentUser(userId);
    }

    /**
     * Soft-deletes the authenticated user. The row is hard-deleted after the grace window.
     *
     * <p>Idempotent. The access JWT stays valid until natural expiry; refresh-revoke kills
     * renewals at once.
     */
    @DeleteMapping("/me")
    @Operation(summary = "Delete the current account (soft-delete with 30-day grace)")
    @ApiResponse(responseCode = "204", description = "Account soft-deleted")
    @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<Void> deleteMe(@AuthenticationPrincipal final Long userId) {
        accountDeletionService.deleteOwnAccount(userId);
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, clearCookie().toString())
            .build();
    }

    /** Rotates the refresh cookie and returns a fresh access JWT. */
    @PostMapping("/refresh")
    @Operation(summary = "Rotate access and refresh tokens")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "New access token; refresh cookie rotated")
    @ApiResponse(responseCode = "401", description = "Missing, expired, or already-rotated refresh token",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
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

    /** Revokes the refresh cookie and clears it from the browser. Idempotent. */
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
     * Starts a password reset. Returns {@code 204} for both known and unknown emails so the
     * response cannot be used to enumerate registered addresses.
     */
    @PostMapping("/forgot-password")
    @Operation(summary = "Start a password reset")
    @SecurityRequirements
    @ApiResponse(responseCode = "204", description = "Reset email dispatched if account exists")
    @ApiResponse(responseCode = "400", description = "Validation failed",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "429", description = "Rate limited",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody final ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.email());
        return ResponseEntity.noContent().build();
    }

    /**
     * Completes a password reset and signs out every other device for the account.
     *
     * <p>Every failure branch returns {@code 400} with the same message so the response cannot
     * be used to tell "wrong token" from "expired" from "already used."
     */
    @PostMapping("/reset-password")
    @Operation(summary = "Complete a password reset")
    @SecurityRequirements
    @ApiResponse(responseCode = "204", description = "Password replaced")
    @ApiResponse(responseCode = "400", description = "Invalid, expired, or already-consumed token",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "429", description = "Rate limited",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody final ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    /** Confirms an email by consuming the verification token. Idempotent on replay. */
    @PostMapping("/verify-email")
    @Operation(summary = "Confirm an email address")
    @SecurityRequirements
    @ApiResponse(responseCode = "204", description = "Email verified or replay accepted")
    @ApiResponse(responseCode = "400", description = "Invalid or expired token",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "429", description = "Rate limited",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody final VerifyEmailRequest request) {
        emailVerificationService.verify(request.token());
        return ResponseEntity.noContent().build();
    }

    /**
     * Resends the verification link. Returns {@code 204} for every branch so the response
     * cannot be used to enumerate registered or unverified emails.
     */
    @PostMapping("/resend-verification")
    @Operation(summary = "Resend an email-verification link")
    @SecurityRequirements
    @ApiResponse(responseCode = "204", description = "Resend email dispatched if account is unverified")
    @ApiResponse(responseCode = "400", description = "Validation failed",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "429", description = "Rate limited",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
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
            .sameSite(cookieProperties.sameSite())
            .path(COOKIE_PATH)
            .maxAge(refreshLifetime)
            .build();
    }

    private ResponseCookie clearCookie() {
        return ResponseCookie.from(COOKIE_NAME, "")
            .httpOnly(true)
            .secure(cookieProperties.secure())
            .sameSite(cookieProperties.sameSite())
            .path(COOKIE_PATH)
            .maxAge(0)
            .build();
    }
}
