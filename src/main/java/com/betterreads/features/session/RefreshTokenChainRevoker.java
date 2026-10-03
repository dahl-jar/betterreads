package com.betterreads.features.session;

import com.betterreads.crypto.HmacTokenHasher;
import com.betterreads.users.SessionRevoker;
import java.time.Instant;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revokes a user's active refresh tokens, optionally keeping one.
 *
 * <p>A separate bean so {@code REQUIRES_NEW} goes through the proxy. A self-call would skip it
 * and the rotate rollback would undo the revoke.
 */
@Component
class RefreshTokenChainRevoker implements SessionRevoker {

    private final RefreshTokenRepository repository;

    private final HmacTokenHasher hasher;

    RefreshTokenChainRevoker(final RefreshTokenRepository repository, final HmacTokenHasher hasher) {
        this.repository = repository;
        this.hasher = hasher;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAllInNewTransaction(final long userId) {
        revokeExcept(userId, null);
    }

    @Transactional
    @Override
    public void revokeAllInCurrentTransaction(final long userId) {
        revokeExcept(userId, null);
    }

    @Transactional
    @Override
    public void revokeOthersInCurrentTransaction(final long userId, final @Nullable String keptRefreshToken) {
        final Long keptId = keptRefreshToken == null
            ? null
            : repository.findByTokenHash(hasher.hash(keptRefreshToken))
                .map(RefreshToken::getRefreshTokenId)
                .orElse(null);
        revokeExcept(userId, keptId);
    }

    private void revokeExcept(final long userId, final @Nullable Long keptId) {
        final Instant now = Instant.now();
        repository.findAllByUserIdAndRevokedAtIsNull(userId).stream()
            .filter(rt -> !rt.getRefreshTokenId().equals(keptId))
            .forEach(rt -> rt.setRevokedAt(now));
    }
}
