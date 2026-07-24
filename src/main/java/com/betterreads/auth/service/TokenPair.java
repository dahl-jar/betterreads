package com.betterreads.auth.service;

import com.betterreads.auth.dto.AuthResponse;

/**
 * Carries both tokens an auth flow produces back to the controller.
 *
 * <p>The refresh token is held apart from {@link AuthResponse} so the controller can write it
 * to the cookie while only {@code body} is serialized as JSON.
 */
public record TokenPair(AuthResponse body, String refreshToken) { }
