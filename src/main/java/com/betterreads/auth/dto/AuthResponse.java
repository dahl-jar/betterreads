package com.betterreads.auth.dto;

/**
 * Response body for register and login. Contains a signed JWT and the authenticated user.
 *
 * @param token signed JWT
 * @param user the authenticated user profile
 */
public record AuthResponse(String token, UserResponse user) { }
