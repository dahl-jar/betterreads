package com.betterreads.auth.dto;

import org.jspecify.annotations.Nullable;

/**
 * User profile returned to clients. The password hash is never included.
 *
 * @param userId persistent user id
 * @param username user handle
 * @param email user email
 * @param displayName optional display name
 * @param avatarUrl optional avatar url
 * @param bio optional bio
 */
public record UserResponse(
    long userId,
    String username,
    String email,
    @Nullable String displayName,
    @Nullable String avatarUrl,
    @Nullable String bio
) { }
