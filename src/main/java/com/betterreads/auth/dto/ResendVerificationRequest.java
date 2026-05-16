package com.betterreads.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for {@code POST /api/v1/auth/resend-verification}. */
public record ResendVerificationRequest(
    @Schema(example = "john.doe@example.com")
    @NotBlank @Email @Size(max = 255) String email
) { }
