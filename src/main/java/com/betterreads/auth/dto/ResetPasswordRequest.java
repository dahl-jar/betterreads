package com.betterreads.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/auth/reset-password}. Password rules match the
 * registration request so a reset cannot downgrade the strength bar.
 */
public record ResetPasswordRequest(
    @NotBlank @Size(max = 128) String token,
    @NotBlank @Size(min = 8, max = 72) String newPassword
) { }
