package com.betterreads.features.account;

import com.betterreads.users.SessionRevoker;
import com.betterreads.users.User;
import com.betterreads.users.UserRepository;

import java.time.Instant;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Soft-deletes a user and revokes their refresh and email tokens in one transaction, so a
 * partial failure cannot leave a half-deleted account.
 */
@Service
class AccountDeletionService {

    private static final Logger LOG = LoggerFactory.getLogger(AccountDeletionService.class);

    private final UserRepository userRepository;

    private final SessionRevoker sessionRevoker;

    private final EmailTokenIssuer tokenIssuer;

    AccountDeletionService(
        final UserRepository userRepository,
        final SessionRevoker sessionRevoker,
        final EmailTokenIssuer tokenIssuer
    ) {
        this.userRepository = userRepository;
        this.sessionRevoker = sessionRevoker;
        this.tokenIssuer = tokenIssuer;
    }

    /**
     * Idempotent. No-op if the user is missing.
     *
     * <p>Skips {@code SELECT ... FOR UPDATE} on {@code app_user} on purpose. The refresh-rotate
     * path locks the refresh row first and then takes an FK lock on the user, so locking the
     * user on delete would deadlock against a concurrent {@code POST /refresh}. A plain
     * {@code UPDATE} only takes {@code FOR NO KEY UPDATE}, which is compatible with rotate.
     *
     * <p>A refresh that races this delete may issue one successor token before the commit
     * lands. That successor is dead on the next call because {@code @SQLRestriction} hides
     * the soft-deleted user, and the row is cleaned up by the sweep.
     */
    @Transactional
    public void deleteOwnAccount(final long userId) {
        userRepository.findById(userId).ifPresentOrElse(
            this::softDelete,
            () -> LOG.info("auth.account-deletion.delete no-op userId={} reason=already-deleted-or-unknown", userId));
    }

    private void softDelete(final User user) {
        final long userId = user.getUserId();
        user.setDeletedAt(Instant.now());
        userRepository.save(user);
        Arrays.stream(EmailToken.Purpose.values())
            .forEach(purpose -> tokenIssuer.consumeOutstanding(userId, purpose));
        sessionRevoker.revokeAllInCurrentTransaction(userId);
        LOG.info("auth.account-deletion.delete soft-deleted userId={}", userId);
    }
}
