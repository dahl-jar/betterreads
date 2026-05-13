package com.betterreads.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/**
 * User profile returned to clients. The password hash and database id are never included;
 * authenticated requests identify the user from the JWT subject claim.
 */
public record UserResponse(
    @Schema(example = "john.doe") String username,
    @Schema(example = "john.doe@example.com") String email,
    @Schema(example = "true") boolean emailVerified,
    @Schema(example = "John Doe") @Nullable String displayName,
    @Schema(example = "https://cdn.example.com/u/john-doe.png") @Nullable String avatarUrl,
    @Schema(example = "Reads mostly sci-fi.") @Nullable String bio
) { }
