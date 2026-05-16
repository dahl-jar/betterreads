package com.betterreads.auth.refresh;

import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revokes every active refresh token for a user.
 *
 * <p>Separate bean so {@code REQUIRES_NEW} actually applies; a self-call inside
 * {@link RefreshTokenService} would skip the proxy and the rotate-path rollback would undo
 * the revoke.
 */
@Component
public class RefreshTokenChainRevoker {

    private final RefreshTokenRepository repository;

    public RefreshTokenChainRevoker(final RefreshTokenRepository repository) {
        this.repository = repository;
    }

    /**
     * Revokes every active refresh token for the user in a new transaction.
     *
     * <p>{@code REQUIRES_NEW} because the rotate path throws right after calling this, and the
     * throw would roll back a same-transaction revoke. The revoke must stick.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAllActiveForUser(final long userId) {
        revoke(userId);
    }

    /**
     * Revokes every active refresh token for the user as part of the surrounding transaction.
     *
     * <p>Used when the revoke must commit or roll back together with the surrounding write
     * (password reset, account deletion).
     */
    @Transactional
    public void revokeAllActiveForUserInTransaction(final long userId) {
        revoke(userId);
    }

    private void revoke(final long userId) {
        final Instant now = Instant.now();
        repository.findAllByUserIdAndRevokedAtIsNull(userId).forEach(rt -> {
            rt.setRevokedAt(now);
            repository.save(rt);
        });
    }
}
