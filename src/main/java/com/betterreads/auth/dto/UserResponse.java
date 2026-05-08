package com.betterreads.auth.dto;

import org.jspecify.annotations.Nullable;

/**
 * User profile returned to clients. The password hash and database id are never included;
 * authenticated requests identify the user from the JWT subject claim.
 */
public record UserResponse(
    String username,
    String email,
    boolean emailVerified,
    @Nullable String displayName,
    @Nullable String avatarUrl,
    @Nullable String bio
) { }
