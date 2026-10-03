package com.betterreads.features.account;

import com.betterreads.errors.RateLimitedResponse;
import com.betterreads.security.RefreshCookies;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RefreshCookies.COOKIE_PATH)
@Tag(name = "Authentication", description = "Register, log in, and look up the current user")
class AccountController {

    private final PasswordResetService passwordResetService;

    private final EmailVerificationService emailVerificationService;

    private final AccountDeletionService accountDeletionService;

    private final PasswordChangeService passwordChangeService;

    private final RefreshCookies refreshCookies;

    AccountController(
            final PasswordResetService passwordResetService,
            final EmailVerificationService emailVerificationService,
            final AccountDeletionService accountDeletionService,
            final PasswordChangeService passwordChangeService,
            final RefreshCookies refreshCookies) {
        this.passwordResetService = passwordResetService;
        this.emailVerificationService = emailVerificationService;
        this.accountDeletionService = accountDeletionService;
        this.passwordChangeService = passwordChangeService;
        this.refreshCookies = refreshCookies;
    }

    /**
     * Soft-deletes the user, and the sweep hard-deletes the row after the grace period.
     *
     * <p>Every session ends at once: refresh tokens are revoked and access tokens of the deleted
     * account are rejected, so a repeat call gets 401.
     */
    @DeleteMapping("/me")
    @Operation(summary = "Delete the current account")
    @ApiResponse(responseCode = "204", description = "Account deleted and every session signed out at once")
    @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<Void> deleteMe(@AuthenticationPrincipal final Long userId) {
        accountDeletionService.deleteOwnAccount(userId);
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, refreshCookies.clear().toString())
            .build();
    }

    @PutMapping("/me/password")
    @Operation(summary = "Change the current account's password")
    @ApiResponse(responseCode = "204", description = "Password changed. Other sessions are signed out at once, "
        + "and this session keeps its refresh token.")
    @ApiResponse(responseCode = "400", description = "Wrong current password or invalid new password",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @RateLimitedResponse
    public ResponseEntity<Void> changePassword(
        @AuthenticationPrincipal final Long userId,
        @Valid @RequestBody final ChangePasswordRequest request,
        @CookieValue(name = RefreshCookies.COOKIE_NAME, required = false) final @Nullable String refreshToken
    ) {
        passwordChangeService.changePassword(
            userId, request.currentPassword(), request.newPassword(), refreshToken);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Start a password reset")
    @SecurityRequirements
    @ApiResponse(responseCode = "204", description = "Reset email dispatched if account exists")
    @ValidationAndRateLimitResponses
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody final ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.email());
        return ResponseEntity.noContent().build();
    }

    /** signs out every device for the account */
    @PostMapping("/reset-password")
    @Operation(summary = "Complete a password reset")
    @SecurityRequirements
    @ApiResponse(responseCode = "204", description = "Password replaced")
    @ApiResponse(responseCode = "400", description = "Invalid, expired, or already-consumed token",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @RateLimitedResponse
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody final ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    /** Idempotent, so a link clicked twice still returns {@code 204}. */
    @PostMapping("/verify-email")
    @Operation(summary = "Confirm an email address")
    @SecurityRequirements
    @ApiResponse(responseCode = "204", description = "Email verified or replay accepted")
    @ApiResponse(responseCode = "400", description = "Invalid or expired token",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @RateLimitedResponse
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody final VerifyEmailRequest request) {
        emailVerificationService.verify(request.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Resend an email-verification link")
    @SecurityRequirements
    @ApiResponse(responseCode = "204", description = "Resend email dispatched if account is unverified")
    @ValidationAndRateLimitResponses
    public ResponseEntity<Void> resendVerification(
        @Valid @RequestBody final ResendVerificationRequest request
    ) {
        emailVerificationService.requestResend(request.email());
        return ResponseEntity.noContent().build();
    }
}
