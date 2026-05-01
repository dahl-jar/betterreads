package com.betterreads.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/v1/auth/login}.
 *
 * <p>{@code identifier} accepts either a username or an email. The service tries username first,
 * then falls back to email.
 *
 * @param identifier username or email
 * @param password raw password
 */
public record LoginRequest(
    @NotBlank String identifier,
    @NotBlank String password
) { }
