package com.betterreads.users;

import org.springframework.stereotype.Component;

@Component
public class AccessTokenCheck {

    private final UserRepository users;

    public AccessTokenCheck(final UserRepository users) {
        this.users = users;
    }

    public boolean isCurrent(final long userId, final int credentialVersion) {
        return users.findById(userId)
            .map(user -> user.getCredentialVersion() == credentialVersion)
            .orElse(false);
    }
}
