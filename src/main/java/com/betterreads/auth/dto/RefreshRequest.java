package com.betterreads.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/auth/refresh} and {@code POST /api/v1/auth/logout}.
 * The size and pattern constraints reject malformed payloads before they hit hashing or the DB.
 */
public record RefreshRequest(
    @NotBlank
    @Size(min = 16, max = 128)
    @Pattern(regexp = "[A-Za-z0-9_-]+")
    String refreshToken
) { }
