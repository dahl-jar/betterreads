package com.betterreads.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/auth/verify-email}. Token size cap matches the
 * password-reset request because both encodings share the same 256-bit HMAC plaintext shape.
 */
public record VerifyEmailRequest(
    @NotBlank @Size(max = 128) String token
) { }
