package com.betterreads.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response body for register, login, and refresh.
 *
 * <p>The refresh token is set on the {@code br_refresh} cookie of the same response.
 */
public record AuthResponse(
    @Schema(example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwiaWF0IjoxNzMwMDAwMDAwfQ.signature",
        description = "Short-lived access JWT")
    String accessToken,

    UserResponse user
) { }
