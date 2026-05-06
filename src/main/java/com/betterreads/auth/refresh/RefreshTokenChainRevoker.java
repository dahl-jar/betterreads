package com.betterreads.auth.refresh;

import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revokes every active refresh token for a user. Lives in its own bean so {@code REQUIRES_NEW}
 * propagation actually applies. Inside {@link RefreshTokenService}, a self-call would bypass
 * the proxy and the rotate-path rollback would undo the revoke.
 */
@Component
public class RefreshTokenChainRevoker {

    private final RefreshTokenRepository repository;

    public RefreshTokenChainRevoker(final RefreshTokenRepository repository) {
        this.repository = repository;
    }

    /**
     * Revokes every active refresh token belonging to the given user.
     *
     * <p>Commits in a new transaction so the revoke survives a rollback in the caller (the
     * rotate path throws {@code BadCredentialsException} after invoking this).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAllActiveForUser(final long userId) {
        revoke(userId);
    }

    /**
     * Revokes every active refresh token belonging to the given user, participating in the
     * caller's transaction. Use from flows where the revoke must be atomic with the surrounding
     * write (password reset, account deletion). The replay-defense path uses
     * {@link #revokeAllActiveForUser(long)} instead so the revoke survives a rollback.
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
