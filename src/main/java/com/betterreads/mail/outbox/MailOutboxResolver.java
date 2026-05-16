package com.betterreads.mail.outbox;

import com.betterreads.common.util.LogSanitizer;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves an outbox row to sent, failed, or scheduled-for-retry.
 *
 * <p>Separate bean so the {@code @Transactional} boundary applies; a self-call inside
 * {@link MailOutboxWorker} would skip it.
 */
@Component
class MailOutboxResolver {

    private static final Logger LOG = LoggerFactory.getLogger(MailOutboxResolver.class);

    private static final int ERROR_TEXT_MAX_LENGTH = 500;

    /**
     * Payload written once a row is done.
     *
     * <p>The original payload holds the plaintext token. Clearing it after the send means a DB
     * read leak cannot hand an attacker a still-redeemable token; the token tables themselves
     * only store HMACs.
     */
    private static final String EMPTY_PAYLOAD = "{}";

    private static final Map<Integer, Duration> RETRY_BACKOFF = Map.of(
        1, Duration.ofMinutes(5),
        2, Duration.ofMinutes(30)
    );

    private static final Duration DEFAULT_BACKOFF = Duration.ofMinutes(30);

    private final MailOutboxRepository repository;

    private final MailOutboxProperties properties;

    MailOutboxResolver(final MailOutboxRepository repository, final MailOutboxProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional
    public void markSent(final long outboxId) {
        repository.findById(outboxId).ifPresent(row -> {
            row.setSentAt(Instant.now());
            row.setLastError(null);
            row.setPayload(EMPTY_PAYLOAD);
            repository.save(row);
        });
    }

    @Transactional
    public void recordFailure(final long outboxId, final int currentAttempt, final MailSendException failure) {
        final String errorText = truncate(failure.getMessage() == null
            ? failure.getClass().getSimpleName() : failure.getMessage());
        repository.findById(outboxId).ifPresent(row -> {
            final boolean atMaxAttempts = currentAttempt >= properties.maxAttempts();
            if (!failure.isRetryable() || atMaxAttempts) {
                row.setFailedAt(Instant.now());
                row.setLastError(errorText);
                row.setPayload(EMPTY_PAYLOAD);
                LOG.error("Outbox row gave up id={} attempts={} retryable={} error={}",
                    outboxId, currentAttempt, failure.isRetryable(), LogSanitizer.forLog(errorText));
            } else {
                final Duration backoff = RETRY_BACKOFF.getOrDefault(currentAttempt, DEFAULT_BACKOFF);
                row.setNextAttemptAt(Instant.now().plus(backoff));
                row.setLastError(errorText);
                LOG.warn("Outbox row scheduled for retry id={} attempt={} backoff={}s",
                    outboxId, currentAttempt, backoff.toSeconds());
            }
            repository.save(row);
        });
    }

    private static String truncate(final String message) {
        return message.length() > ERROR_TEXT_MAX_LENGTH
            ? message.substring(0, ERROR_TEXT_MAX_LENGTH) : message;
    }
}
