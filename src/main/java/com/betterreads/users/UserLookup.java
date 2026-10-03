package com.betterreads.users;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;

@Component
public class UserLookup {

    private static final Logger LOG = LoggerFactory.getLogger(UserLookup.class);

    private static final String SESSION_NO_LONGER_VALID = "Session no longer valid";

    private final UserRepository users;

    public UserLookup(final UserRepository users) {
        this.users = users;
    }

    public User require(final long userId) {
        return users.findById(userId).orElseThrow(() -> sessionGone(userId));
    }

    public User requireLocked(final long userId) {
        return users.findByIdForUpdate(userId).orElseThrow(() -> sessionGone(userId));
    }

    private static BadCredentialsException sessionGone(final long userId) {
        LOG.warn("User deleted or missing userId={}", userId);
        return new BadCredentialsException(SESSION_NO_LONGER_VALID);
    }
}
