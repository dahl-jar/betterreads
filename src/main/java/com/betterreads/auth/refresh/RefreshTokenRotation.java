package com.betterreads.auth.refresh;

/**
 * Result of a successful refresh-token rotation: the user the token belonged to, and the new
 * plaintext refresh token to return to the client.
 */
public record RefreshTokenRotation(long userId, String plaintext) { }
