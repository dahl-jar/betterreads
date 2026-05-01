package com.betterreads.auth.mapper;

import com.betterreads.auth.dto.UserResponse;
import com.betterreads.auth.entity.User;

import org.springframework.stereotype.Component;

/**
 * Converts {@link User} entities to {@link UserResponse} DTOs.
 */
@Component
public class UserMapper {

    /**
     * Maps a {@link User} to its public response shape.
     */
    public UserResponse toResponse(final User user) {
        return new UserResponse(
            user.getUserId(),
            user.getUsername(),
            user.getEmail(),
            user.getDisplayName(),
            user.getAvatarUrl(),
            user.getBio()
        );
    }
}
