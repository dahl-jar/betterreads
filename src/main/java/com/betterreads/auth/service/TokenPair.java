package com.betterreads.auth.service;

import com.betterreads.auth.dto.AuthResponse;

/**
 * Internal carrier for the two tokens an auth flow produces. The {@link #body} reaches the client
 * as JSON; the {@link #refreshToken} reaches the client as an {@code HttpOnly} cookie. Splitting
 * the two at the service boundary keeps the JSON body honest about what's serialized.
 *
 * @param body response body returned to the caller
 * @param refreshToken refresh token plaintext written to the {@code br_refresh} cookie
 */
public record TokenPair(AuthResponse body, String refreshToken) { }
