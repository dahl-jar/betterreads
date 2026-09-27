package com.betterreads.users;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolves user ids to usernames. */
@Service
public class UsernameLookup {

    private final UserRepository users;

    public UsernameLookup(final UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public Optional<String> usernameOf(final long userId) {
        return users.findById(userId).map(User::getUsername);
    }

    /** Ids with no user are left out. */
    @Transactional(readOnly = true)
    public Map<Long, String> usernamesByIds(final Collection<Long> userIds) {
        return users.findAllById(userIds).stream()
            .collect(Collectors.toMap(User::getUserId, User::getUsername));
    }
}
