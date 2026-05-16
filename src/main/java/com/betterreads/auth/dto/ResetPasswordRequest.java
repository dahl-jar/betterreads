package com.betterreads.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/auth/reset-password}.
 *
 * <p>Password rules match the registration request so a reset cannot set a weaker password
 * than register would have allowed.
 */
public record ResetPasswordRequest(
    @Schema(example = "8f2a3c4e5d6b7a8f2a3c4e5d6b7a8f2a", description = "Reset token from the email link")
    @NotBlank @Size(max = 128) String token,

    @Schema(example = "********", description = "8-72 bytes")
    @NotBlank @Size(min = 8, max = 72) String newPassword
) { }
