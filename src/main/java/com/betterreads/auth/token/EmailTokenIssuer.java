package com.betterreads.auth.token;

import com.betterreads.common.crypto.HmacTokenHasher;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;

/**
 * Issues single-use email tokens, leaving one active token per user and purpose.
 *
 * <p>Joins the caller's transaction, which must hold the user row locked. Without that
 * serialization two concurrent issues could both insert an active token and violate the partial
 * unique index.
 */
@Component
public class EmailTokenIssuer {

    private static final int TOKEN_BYTES = 32;

    private final EmailTokenRepository repository;

    private final HmacTokenHasher hasher;

    public EmailTokenIssuer(final EmailTokenRepository repository, final HmacTokenHasher hasher) {
        this.repository = repository;
        this.hasher = hasher;
    }

    /**
     * Consumes the user's outstanding tokens for the purpose, inserts a fresh one, and returns
     * its plaintext. The database keeps only the hash, so the return value is the only copy.
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
    private void consumeOutstanding(final long userId, final EmailToken.Purpose purpose) {
        final Instant now = Instant.now();
        repository.findActive(userId, purpose).forEach(token -> {
            token.setConsumedAt(now);
            repository.save(token);
        });
        repository.flush();
    }
}
