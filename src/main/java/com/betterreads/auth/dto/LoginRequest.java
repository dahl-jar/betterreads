package com.betterreads.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/v1/auth/login}. {@code identifier} accepts a username or
 * email; the service tries username first, then email.
 */
public record LoginRequest(
    @NotBlank String identifier,
    @NotBlank String password
) { }
