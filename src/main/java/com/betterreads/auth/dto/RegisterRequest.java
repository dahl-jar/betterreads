package com.betterreads.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/auth/register}.
 *
 * <p>Usernames cannot contain {@code @} so they can't be confused with emails during login
 * (login accepts either and tries username first). Password is capped at 72 bytes to match
 * BCrypt's input limit.
 *
 * @param username 3-50 characters, alphanumeric plus underscore, dot, and hyphen
 * @param email valid email address, max 255 characters
 * @param password raw password, 8-72 characters
 */
public record RegisterRequest(
    @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "[A-Za-z0-9._-]+") String username,
    @NotBlank @Email @Size(max = 255) String email,
    @NotBlank @Size(min = 8, max = 72) String password
) { }
