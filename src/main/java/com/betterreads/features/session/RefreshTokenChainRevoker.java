package com.betterreads.features.session;

import com.betterreads.users.SessionRevoker;
import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revokes every active refresh token for a user.
 *
 * <p>A separate bean so {@code REQUIRES_NEW} goes through the proxy. A self-call would skip it
 * and the rotate rollback would undo the revoke.
 */
@Component
class RefreshTokenChainRevoker implements SessionRevoker {

    private final RefreshTokenRepository repository;

    RefreshTokenChainRevoker(final RefreshTokenRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAllInNewTransaction(final long userId) {
        revoke(userId);
    }

    @Transactional
    @Override
    public void revokeAllInCurrentTransaction(final long userId) {
        revoke(userId);
    }

    private void revoke(final long userId) {
        final Instant now = Instant.now();
        repository.findAllByUserIdAndRevokedAtIsNull(userId).forEach(rt -> rt.setRevokedAt(now));
    }
}
