package com.betterreads.features.session;

import com.betterreads.crypto.HmacTokenHasher;
import com.betterreads.crypto.TokenGenerator;
import com.betterreads.security.JwtProperties;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Issues, rotates and revokes refresh tokens, stored as HMAC-SHA256 hashes. */
@Service
class RefreshTokenService {

    private static final Logger LOG = LoggerFactory.getLogger(RefreshTokenService.class);

    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository repository;

    private final HmacTokenHasher hasher;

    private final RefreshTokenChainRevoker chainRevoker;

    private final Duration lifetime;

    RefreshTokenService(
        final RefreshTokenRepository repository,
        final HmacTokenHasher hasher,
        final RefreshTokenChainRevoker chainRevoker,
        final JwtProperties jwtProperties
    ) {
        this.repository = repository;
        this.hasher = hasher;
        this.chainRevoker = chainRevoker;
        this.lifetime = Duration.ofDays(jwtProperties.refreshExpirationDays());
    }

    /** Returns the plaintext, only its hash is saved. */
    @Transactional
    public String issue(final long userId) {
        final String plaintext = TokenGenerator.randomToken(TOKEN_BYTES);
        repository.save(newToken(userId, plaintext));
        LOG.info("Issued refresh token userId={}", userId);
        return plaintext;
    }

    /**
     * Empty for an unknown, expired or revoked token. An already rotated token is a replay,
     * so every active token of that user gets revoked.
     */
    @Transactional
    public Optional<RefreshTokenRotation> rotate(final String presented) {
        final Optional<RefreshToken> rowOpt = repository.findByTokenHashForUpdate(hasher.hash(presented));
        if (rowOpt.isEmpty()) {
            return Optional.empty();
        }
        final RefreshToken row = rowOpt.get();

        if (row.getRevokedAt() != null) {
            if (row.getReplacedBy() != null) {
                LOG.warn("Refresh token replayed, revoking all user tokens userId={} tokenId={}",
                    row.getUserId(), row.getRefreshTokenId());
                chainRevoker.revokeAllInNewTransaction(row.getUserId());
            }
            return Optional.empty();
        }

        if (row.getExpiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }

        final String newPlaintext = TokenGenerator.randomToken(TOKEN_BYTES);
        final RefreshToken saved = repository.saveAndFlush(newToken(row.getUserId(), newPlaintext));

        row.setRevokedAt(Instant.now());
        row.setReplacedBy(saved.getRefreshTokenId());
        repository.saveAndFlush(row);

        LOG.info("Rotated refresh token userId={}", row.getUserId());
        return Optional.of(new RefreshTokenRotation(row.getUserId(), newPlaintext));
    }

    /** unknown and already revoked tokens are ignored so logout can't be used to probe which tokens exist */
    @Transactional
    public void revoke(final String presented) {
        repository.findByTokenHash(hasher.hash(presented))
            .filter(row -> row.getRevokedAt() == null)
            .ifPresent(row -> {
                row.setRevokedAt(Instant.now());
                LOG.info("Revoked refresh token userId={}", row.getUserId());
            });
    }

    private RefreshToken newToken(final long userId, final String plaintext) {
        final RefreshToken row = new RefreshToken();
        row.setUserId(userId);
        row.setTokenHash(hasher.hash(plaintext));
        row.setExpiresAt(Instant.now().plus(lifetime));
        return row;
    }
}
