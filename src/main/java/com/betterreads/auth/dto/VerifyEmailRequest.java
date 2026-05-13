package com.betterreads.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/auth/verify-email}. Token size cap matches the
 * password-reset request because both encodings share the same 256-bit HMAC plaintext shape.
 */
public record VerifyEmailRequest(
    @Schema(example = "8f2a3c4e5d6b7a8f2a3c4e5d6b7a8f2a", description = "Verification token from the email link")
    @NotBlank @Size(max = 128) String token
) { }
