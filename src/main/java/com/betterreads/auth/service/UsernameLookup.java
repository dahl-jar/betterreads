package com.betterreads.auth.service;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.betterreads.auth.entity.User;
import com.betterreads.auth.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolves user ids to usernames. */
@Service
public class UsernameLookup {

    private final UserRepository users;

    public UsernameLookup(final UserRepository users) {
        this.users = users;
    }

    /** Returns the username for the id, or empty when no user has it. */
    @Transactional(readOnly = true)
    public Optional<String> usernameOf(final long userId) {
        return users.findById(userId).map(User::getUsername);
    }

    /** Returns the username per id among the given ids, omitting ids with no user. */
    @Transactional(readOnly = true)
    public Map<Long, String> usernamesByIds(final Collection<Long> userIds) {
        return users.findAllById(userIds).stream()
            .collect(Collectors.toMap(User::getUserId, User::getUsername));
    }
}
