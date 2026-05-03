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
        final Instant now = Instant.now();
        repository.findAllByUserId(userId).forEach(rt -> {
            if (rt.getRevokedAt() == null) {
                rt.setRevokedAt(now);
                repository.save(rt);
            }
        });
    }
}
