package com.betterreads.features.account;

import com.betterreads.errors.InvalidRequestException;
import com.betterreads.users.SessionRevoker;
import com.betterreads.users.User;
import com.betterreads.users.UserLookup;
import com.betterreads.users.UserPasswords;
import com.betterreads.users.UserRepository;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PasswordChangeService {

    private static final Logger LOG = LoggerFactory.getLogger(PasswordChangeService.class);

    private static final String WRONG_CURRENT_PASSWORD = "Current password is incorrect";

    private final UserRepository userRepository;

    private final UserLookup userLookup;

    private final UserPasswords userPasswords;

    private final SessionRevoker sessionRevoker;

    PasswordChangeService(
        final UserRepository userRepository,
        final UserLookup userLookup,
        final UserPasswords userPasswords,
        final SessionRevoker sessionRevoker
    ) {
        this.userRepository = userRepository;
        this.userLookup = userLookup;
        this.userPasswords = userPasswords;
        this.sessionRevoker = sessionRevoker;
    }

    @Transactional
    public void changePassword(
        final long userId,
        final String currentPassword,
        final String newPassword,
        final @Nullable String refreshToken
    ) {
        final User user = userLookup.requireLocked(userId);
        if (!userPasswords.matches(user, currentPassword)) {
            LOG.warn("Password change rejected: wrong current password userId={}", userId);
            throw new InvalidRequestException(WRONG_CURRENT_PASSWORD);
        }
        userPasswords.setPassword(user, newPassword);
        userRepository.save(user);
        sessionRevoker.revokeOthersInCurrentTransaction(userId, refreshToken);
        LOG.info("Password changed, other refresh tokens revoked userId={}", userId);
    }
}
