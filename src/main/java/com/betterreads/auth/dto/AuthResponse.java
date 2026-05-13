package com.betterreads.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response body for register, login, and refresh: the short-lived access JWT and the user
 * profile. The refresh token does not appear here; it travels in the {@code br_refresh}
 * {@code HttpOnly} cookie attached to the same response.
 */
public record AuthResponse(
    @Schema(example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwiaWF0IjoxNzMwMDAwMDAwfQ.signature",
        description = "Short-lived access JWT")
    String accessToken,

    UserResponse user
) { }
