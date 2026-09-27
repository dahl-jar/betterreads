package com.betterreads.users;

import org.springframework.stereotype.Component;

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
