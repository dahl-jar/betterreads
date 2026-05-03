package com.betterreads.auth.dto;

import org.jspecify.annotations.Nullable;

/**
 * User profile returned to clients. The password hash is never included.
 */
public record UserResponse(
    long userId,
    String username,
    String email,
    @Nullable String displayName,
    @Nullable String avatarUrl,
    @Nullable String bio
) { }
