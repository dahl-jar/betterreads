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
 * Soft-deletes a user and tears down their auth material in a single transaction so a partial
 * failure cannot leave a half-deleted account. Revokes every refresh token, marks outstanding
 * password-reset and email-verification tokens consumed, and stamps {@code deleted_at} on the
 * {@code app_user} row.
 *
 * <p>The access JWT remains valid until natural expiry (today 2 hours); refresh-revoke kills
 * the renewal path immediately, and the residual window is the documented price of stateless
 * validation.
 *
 * <p>The hard-delete sweep is a separate concern; see {@link AccountDeletionSweep}.
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
     * Soft-deletes the user and tears down active auth material. Idempotent: a second call for
     * an already-deleted user returns silently. Has no effect if the user does not exist.
     *
     * <p>Reads {@code app_user} without {@code SELECT ... FOR UPDATE} on purpose. The
     * project's lock order for email-token mutations is "user first, then tokens", but the
     * refresh-rotation path locks the {@code refresh_token} row first and then takes an FK
     * lock on {@code app_user} for its successor INSERT. Holding a user {@code FOR UPDATE}
     * here while updating refresh-token rows therefore deadlocks against a concurrent
     * {@code POST /refresh} for the same account. Plain {@code UPDATE} on {@code app_user}
     * only takes {@code FOR NO KEY UPDATE}, which is compatible with rotate's
     * {@code FOR KEY SHARE}, so dropping the row lock removes the deadlock.
     *
     * <p>Race-window orphans are tolerated. A refresh started before this transaction commits
     * can issue a successor refresh token and a fresh access JWT. That successor is unusable
     * for any future {@code /refresh} because the {@link User} {@code @SQLRestriction} hides
     * the soft-deleted row from {@code findById}, so {@code AuthService.refresh} returns 401
     * on the next call. The orphan row is cascade-deleted when the sweep removes the user.
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
