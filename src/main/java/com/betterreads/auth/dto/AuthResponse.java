package com.betterreads.auth.dto;

/**
 * Response body for register, login, and refresh: the short-lived access JWT and the user
 * profile. The refresh token does not appear here; it travels in the {@code br_refresh}
 * {@code HttpOnly} cookie attached to the same response.
 */
public record AuthResponse(String accessToken, UserResponse user) { }
