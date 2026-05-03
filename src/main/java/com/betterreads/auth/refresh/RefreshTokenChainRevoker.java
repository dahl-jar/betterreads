package com.betterreads.auth.refresh;

import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revokes every active refresh token for a user in its own transaction.
 *
 * <p>Lives in a separate Spring bean so the {@code REQUIRES_NEW} propagation actually applies.
 * If this method lived on {@link RefreshTokenService}, the calling rotate path's transaction
 * would roll back when the surrounding {@code BadCredentialsException} fires, undoing the
 * revocation. A separate bean ensures the chain-revoke commits independently.
 */
@Component
public class RefreshTokenChainRevoker {

    private final RefreshTokenRepository repository;

    public RefreshTokenChainRevoker(final RefreshTokenRepository repository) {
        this.repository = repository;
    }

    /**
     * Marks every active refresh token for the given user as revoked. Commits in a new
     * transaction so the result survives a rollback in the caller.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAllActiveForUser(final long userId) {
        final Instant now = Instant.now();
        repository.findAllByUserId(userId).forEach(rt -> {
            if (rt.getRevokedAt() == null) {
                rt.setRevokedAt(now);
                repository.save(rt);
            }
        });
    }
}
