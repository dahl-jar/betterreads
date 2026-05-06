package com.betterreads.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/auth/forgot-password}. The endpoint always returns
 * {@code 204}; whether the email matches an account is never reflected in the response.
 */
public record ForgotPasswordRequest(
    @NotBlank @Email @Size(max = 255) String email
) { }
