package com.betterreads.features.account;

import com.betterreads.security.RefreshCookies;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
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

    private final RefreshCookies refreshCookies;

    AccountController(
            final PasswordResetService passwordResetService,
            final EmailVerificationService emailVerificationService,
            final AccountDeletionService accountDeletionService,
            final RefreshCookies refreshCookies) {
        this.passwordResetService = passwordResetService;
        this.emailVerificationService = emailVerificationService;
        this.accountDeletionService = accountDeletionService;
        this.refreshCookies = refreshCookies;
    }

    /**
     * Soft-deletes the user, and the sweep hard-deletes the row after the grace period.
     *
     * <p>Idempotent. The access JWT stays valid until it expires, so revoking the refresh
     * token cuts renewals off right away.
     */
    @DeleteMapping("/me")
    @Operation(summary = "Delete the current account")
    @ApiResponse(responseCode = "204", description = "Account deleted")
    @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<Void> deleteMe(@AuthenticationPrincipal final Long userId) {
        accountDeletionService.deleteOwnAccount(userId);
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, refreshCookies.clear().toString())
            .build();
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
    @ApiResponse(responseCode = "429", description = "Rate limited",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
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
    @ApiResponse(responseCode = "429", description = "Rate limited",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
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
