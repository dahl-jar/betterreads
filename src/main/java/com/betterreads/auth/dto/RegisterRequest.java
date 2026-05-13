package com.betterreads.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/auth/register}. The username pattern excludes {@code @}
 * so login (which accepts username or email) can disambiguate. Password caps at 72 bytes to
 * match BCrypt's input limit.
 */
public record RegisterRequest(
    @Schema(example = "john.doe", description = "3-50 chars: letters, digits, '.', '_', '-'")
    @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "[A-Za-z0-9._-]+") String username,

    @Schema(example = "john.doe@example.com")
    @NotBlank @Email @Size(max = 255) String email,

    @Schema(example = "********", description = "8-72 bytes")
    @NotBlank @Size(min = 8, max = 72) String password
) { }
