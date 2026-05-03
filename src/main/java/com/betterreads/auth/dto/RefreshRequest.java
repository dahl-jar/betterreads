package com.betterreads.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/auth/refresh} and {@code POST /api/v1/auth/logout}.
 *
 * <p>The token is base64url without padding from a 32-byte secret, so well-formed values are
 * exactly 43 characters of {@code [A-Za-z0-9_-]}. The constraints reject oversized or
 * malformed payloads at the API boundary so the hashing and DB lookup never run on garbage.
 *
 * @param refreshToken the opaque refresh token previously issued by login or refresh
 */
public record RefreshRequest(
    @NotBlank
    @Size(min = 16, max = 128)
    @Pattern(regexp = "[A-Za-z0-9_-]+")
    String refreshToken
) { }
