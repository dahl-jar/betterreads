package com.betterreads.auth.mapper;

import com.betterreads.auth.dto.UserResponse;
import com.betterreads.auth.entity.User;

import org.springframework.stereotype.Component;

/**
 * Converts {@link User} entities to {@link UserResponse} DTOs.
 */
@Component
public class UserMapper {

    public UserResponse toResponse(final User user) {
        return new UserResponse(
            user.getUsername(),
            user.getEmail(),
            user.getEmailVerifiedAt() != null,
            user.getDisplayName(),
            user.getAvatarUrl(),
            user.getBio()
        );
    }
}
