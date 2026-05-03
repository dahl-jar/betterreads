package com.betterreads.auth.refresh;

import com.betterreads.auth.jwt.JwtProperties;

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
 * <p>The plaintext token returned to the client is a 256-bit random opaque string. Only its
 * {@link RefreshTokenHasher HMAC-SHA256 hash} is persisted, so a database read leak alone
 * cannot reconstruct active tokens.
 *
 * <p>Rotation chains are tracked via {@link RefreshToken#getReplacedBy()}. If a client presents
 * a token that was already rotated (revoked + replaced), every refresh token belonging to that
 * user is revoked. This catches the case where one of two parties has the original token and
 * is racing the legitimate user.
 */
@Service
public class RefreshTokenService {

    private static final Logger LOG = LoggerFactory.getLogger(RefreshTokenService.class);

    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository repository;

    private final RefreshTokenHasher hasher;

    private final RefreshTokenChainRevoker chainRevoker;

    private final SecureRandom random;

    private final Duration lifetime;

    /**
     * Constructor wiring. {@code SecureRandom} is created here rather than injected because the
     * default JVM source is correct and there's no test reason to swap it.
     */
    public RefreshTokenService(
        final RefreshTokenRepository repository,
        final RefreshTokenHasher hasher,
        final RefreshTokenChainRevoker chainRevoker,
        final JwtProperties jwtProperties
    ) {
        this.repository = repository;
        this.hasher = hasher;
        this.chainRevoker = chainRevoker;
        this.random = new SecureRandom();
        this.lifetime = Duration.ofDays(jwtProperties.refreshExpirationDays());
    }

    /**
     * Issues a fresh refresh token for the given user. Returns the plaintext token; only the
     * hash is persisted.
     */
    @Transactional
    public String issue(final long userId) {
        final String plaintext = generatePlaintext();
        final RefreshToken row = new RefreshToken();
        row.setUserId(userId);
        row.setTokenHash(hasher.hash(plaintext));
        row.setExpiresAt(Instant.now().plus(lifetime));
        repository.save(row);
        LOG.info("auth.refresh.issue userId={}", userId);
        return plaintext;
    }

    /**
     * Validates a presented refresh token and rotates it. Returns the user id along with a new
     * plaintext refresh token. Empty if the token is unknown, expired, revoked, or belongs to a
     * deleted user. Presenting an already-replaced token revokes the entire chain for that user.
     *
     * <p>Uses {@code SELECT ... FOR UPDATE} on the lookup so two concurrent rotation requests
     * presenting the same active token serialize. Without the lock, both transactions could
     * pass the {@code revokedAt == null} check and issue successors, leaving two active tokens
     * and bypassing the replay defense for the racing client.
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
                LOG.warn("auth.refresh.replay userId={} tokenId={}", row.getUserId(), row.getRefreshTokenId());
                chainRevoker.revokeAllActiveForUser(row.getUserId());
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

        LOG.info("auth.refresh.rotate userId={}", row.getUserId());
        return Optional.of(new RefreshTokenRotation(row.getUserId(), newPlaintext));
    }

    /**
     * Marks the presented refresh token as revoked. No-op if the token is unknown or already
     * revoked. Always returns success-without-detail to avoid leaking which tokens existed.
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
        LOG.info("auth.refresh.revoke userId={}", row.getUserId());
    }

    private String generatePlaintext() {
        final byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
