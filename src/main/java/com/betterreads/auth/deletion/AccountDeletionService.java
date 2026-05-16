package com.betterreads.auth.deletion;

import com.betterreads.auth.entity.User;
import com.betterreads.auth.refresh.RefreshTokenChainRevoker;
import com.betterreads.auth.repository.UserRepository;
import com.betterreads.auth.token.EmailToken;
import com.betterreads.auth.token.EmailTokenRepository;

import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Soft-deletes a user and revokes their refresh and email tokens in one transaction.
 *
 * <p>Done in one transaction so a partial failure cannot leave a half-deleted account. The
 * access JWT stays valid until it expires naturally; the refresh revoke kills renewals at
 * once.
 */
@Service
public class AccountDeletionService {

    private static final Logger LOG = LoggerFactory.getLogger(AccountDeletionService.class);

    private final UserRepository userRepository;

    private final RefreshTokenChainRevoker refreshTokenChainRevoker;

    private final EmailTokenRepository emailTokenRepository;

    public AccountDeletionService(
        final UserRepository userRepository,
        final RefreshTokenChainRevoker refreshTokenChainRevoker,
        final EmailTokenRepository emailTokenRepository
    ) {
        this.userRepository = userRepository;
        this.refreshTokenChainRevoker = refreshTokenChainRevoker;
        this.emailTokenRepository = emailTokenRepository;
    }

    /**
     * Soft-deletes the user and revokes their tokens. Idempotent. No-op if the user is missing.
     *
     * <p>Skips {@code SELECT ... FOR UPDATE} on {@code app_user} on purpose. The refresh-rotate
     * path locks the refresh row first and then takes an FK lock on the user, so locking the
     * user here would deadlock against a concurrent {@code POST /refresh}. A plain
     * {@code UPDATE} only takes {@code FOR NO KEY UPDATE}, which is compatible with rotate.
     *
     * <p>A refresh that races this delete may issue one successor token before the commit
     * lands. That successor is dead on the next call because {@code @SQLRestriction} hides
     * the soft-deleted user, and the row is cleaned up by the sweep.
     */
    @Transactional
    public void deleteOwnAccount(final long userId) {
        final Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            LOG.info("auth.account-deletion.delete no-op userId={} reason=already-deleted-or-unknown", userId);
            return;
        }
        final User user = userOpt.get();
        final Instant now = Instant.now();
        user.setDeletedAt(now);
        userRepository.save(user);
        invalidateOutstandingEmailTokens(userId, now);
        refreshTokenChainRevoker.revokeAllActiveForUserInTransaction(userId);
        LOG.info("auth.account-deletion.delete soft-deleted userId={}", userId);
    }

    private void invalidateOutstandingEmailTokens(final long userId, final Instant now) {
        for (final EmailToken.Purpose purpose : EmailToken.Purpose.values()) {
            emailTokenRepository.findActive(userId, purpose).forEach(token -> {
                token.setConsumedAt(now);
                emailTokenRepository.save(token);
            });
        }
    }
}
