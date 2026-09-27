package com.betterreads.features.account;

import com.betterreads.crypto.HmacTokenHasher;
import com.betterreads.crypto.TokenGenerator;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;

/**
 * Issues single-use email tokens, leaving one active token per user and purpose.
 *
 * <p>Runs in the caller's transaction, which holds the user row locked, so two concurrent issues
 * cannot both insert an active token and break the partial unique index.
 */
@Component
class EmailTokenIssuer {

    private static final int TOKEN_BYTES = 32;

    private final EmailTokenRepository repository;

    private final HmacTokenHasher hasher;

    EmailTokenIssuer(final EmailTokenRepository repository, final HmacTokenHasher hasher) {
        this.repository = repository;
        this.hasher = hasher;
    }

    /**
     * Returns the plaintext of a fresh token and consumes the older ones. The database keeps
     * only the hash, so the return value is the only copy.
     */
    public String issue(
        final long userId,
        final EmailToken.Purpose purpose,
        final Duration lifetime
    ) {
        consumeOutstanding(userId, purpose);
        final String plaintext = TokenGenerator.randomToken(TOKEN_BYTES);
        final EmailToken row = new EmailToken();
        row.setUserId(userId);
        row.setPurpose(purpose);
        row.setTokenHash(hasher.hash(plaintext));
        row.setExpiresAt(Instant.now().plus(lifetime));
        repository.saveAndFlush(row);
        return plaintext;
    }

    /**
     * Flushes at the end because Hibernate's default action queue runs inserts before updates,
     * so the next insert would hit the partial unique index against the still-active prior row.
     */
    void consumeOutstanding(final long userId, final EmailToken.Purpose purpose) {
        final Instant now = Instant.now();
        repository.findActive(userId, purpose).forEach(token -> {
            token.setConsumedAt(now);
            repository.save(token);
        });
        repository.flush();
    }
}
