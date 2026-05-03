package com.betterreads.auth.dto;

/**
 * Response body for register, login, and refresh. Carries a short-lived access JWT, a
 * long-lived opaque refresh token, and the authenticated user profile.
 *
 * @param accessToken signed HS256 JWT, lifetime governed by {@code jwt.expiration-minutes}
 * @param refreshToken opaque refresh token, lifetime governed by {@code jwt.refresh-expiration-days}
 * @param user the authenticated user profile
 */
public record AuthResponse(String accessToken, String refreshToken, UserResponse user) { }
