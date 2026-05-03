package com.betterreads.auth.refresh;

/**
 * Result of a successful refresh-token rotation.
 */
public record RefreshTokenRotation(long userId, String plaintext) { }
