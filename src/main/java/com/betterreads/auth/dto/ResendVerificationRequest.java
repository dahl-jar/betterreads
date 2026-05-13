package com.betterreads.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/auth/resend-verification}. The endpoint always returns
 * {@code 204}; whether the email matches an unverified account is never reflected in the
 * response.
 */
public record ResendVerificationRequest(
    @Schema(example = "john.doe@example.com")
    @NotBlank @Email @Size(max = 255) String email
) { }
