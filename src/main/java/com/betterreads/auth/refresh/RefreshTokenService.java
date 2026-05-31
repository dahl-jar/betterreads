package com.betterreads.auth.refresh;

import com.betterreads.auth.jwt.JwtProperties;
import com.betterreads.common.crypto.HmacTokenHasher;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues, rotates, and revokes refresh tokens.
 *
 * <p>Only the HMAC-SHA256 hash is stored. Presenting an already-rotated token revokes every
 * active token for that user as replay defense.
 */
@Service
public class RefreshTokenService {

    private static final Logger LOG = LoggerFactory.getLogger(RefreshTokenService.class);

    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository repository;

    private final HmacTokenHasher hasher;

    private final RefreshTokenChainRevoker chainRevoker;

    private final SecureRandom random;

    private final Duration lifetime;

    public RefreshTokenService(
        final RefreshTokenRepository repository,
        final HmacTokenHasher hasher,
        final RefreshTokenChainRevoker chainRevoker,
        final JwtProperties jwtProperties
    ) {
        this.repository = repository;
        this.hasher = hasher;
        this.chainRevoker = chainRevoker;
        this.random = new SecureRandom();
        this.lifetime = Duration.ofDays(jwtProperties.refreshExpirationDays());
    }

    /** Issues a refresh token for the user and returns its plaintext. */
    @Transactional
    public String issue(final long userId) {
        final String plaintext = generatePlaintext();
        final RefreshToken row = new RefreshToken();
        row.setUserId(userId);
        row.setTokenHash(hasher.hash(plaintext));
        row.setExpiresAt(Instant.now().plus(lifetime));
        repository.save(row);
        LOG.info("Issued refresh token userId={}", userId);
        return plaintext;
    }

    /**
     * Rotates the presented refresh token, or returns empty if it is unknown, expired, or
     * already revoked. A replay (token with {@code replacedBy} set) revokes the whole chain.
     *
     * <p>{@code SELECT ... FOR UPDATE} so two concurrent rotations serialize; without the lock
     * both could pass the {@code revokedAt == null} check and issue two successors.
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
                LOG.warn("Refresh token replayed, revoking entire chain userId={} tokenId={}",
                    row.getUserId(), row.getRefreshTokenId());
                chainRevoker.revokeAllInNewTransaction(row.getUserId());
            }
            return Optional.empty();
        }

        if (row.getExpiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }

        final String newPlaintext = generatePlaintext();
        final RefreshToken successor = new RefreshToken();
        successor.setUserId(row.getUserId());
        successor.setTokenHash(hasher.hash(newPlaintext));
        successor.setExpiresAt(Instant.now().plus(lifetime));
        final RefreshToken saved = repository.saveAndFlush(successor);

        row.setRevokedAt(Instant.now());
        row.setReplacedBy(saved.getRefreshTokenId());
        repository.saveAndFlush(row);

        LOG.info("Rotated refresh token userId={}", row.getUserId());
        return Optional.of(new RefreshTokenRotation(row.getUserId(), newPlaintext));
    }

    /**
     * Revokes the presented refresh token. Idempotent.
     *
     * <p>Unknown and already-revoked tokens are accepted silently so the response cannot be
     * used to probe which tokens exist.
     */
    @Transactional
    public void revoke(final String presented) {
        final Optional<RefreshToken> rowOpt = repository.findByTokenHash(hasher.hash(presented));
        if (rowOpt.isEmpty()) {
            return;
        }
        final RefreshToken row = rowOpt.get();
        if (row.getRevokedAt() != null) {
            return;
        }
        row.setRevokedAt(Instant.now());
        repository.save(row);
        LOG.info("Revoked refresh token userId={}", row.getUserId());
    }

    private String generatePlaintext() {
        final byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
