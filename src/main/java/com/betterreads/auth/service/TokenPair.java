package com.betterreads.auth.service;

import com.betterreads.auth.dto.AuthResponse;

/**
 * Carries both tokens an auth flow produces back to the controller.
 *
 * <p>Split so the JSON body and the {@code HttpOnly} refresh cookie are serialized at the
 * right layer; mixing them in a single response DTO leaks the refresh token into JSON.
 */
public record TokenPair(AuthResponse body, String refreshToken) { }
