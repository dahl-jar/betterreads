package com.betterreads.auth.dto;

/**
 * Response body for register, login, and refresh: a short-lived access JWT, a long-lived
 * opaque refresh token, and the user profile.
 */
public record AuthResponse(String accessToken, String refreshToken, UserResponse user) { }
