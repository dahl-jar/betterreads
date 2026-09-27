package com.betterreads.mailoutbox;

import com.betterreads.clients.mail.MailSendException;
import com.betterreads.logging.LogSanitizer;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Marks an outbox row sent, failed, or due for a retry. A self-call from the worker would skip
 * {@code @Transactional}, so this is a separate bean.
 */
@Component
class MailOutboxResolver {

    private static final Logger LOG = LoggerFactory.getLogger(MailOutboxResolver.class);

    private static final int ERROR_TEXT_MAX_LENGTH = 500;

    /**
     * The payload holds the plaintext token and the token tables only store HMACs, so a finished
     * row gets an empty payload and a leaked DB read has no usable token.
     */
    private static final String EMPTY_PAYLOAD = "{}";

    private static final Duration FIRST_RETRY_BACKOFF = Duration.ofMinutes(5);

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

    /** Schedules a retry, or gives up on a permanent failure or the last attempt. */
    @Transactional
    public void recordFailure(final long outboxId, final int currentAttempt, final MailSendException failure) {
        final String errorText = truncate(failure.getMessage() == null
            ? failure.getClass().getSimpleName() : failure.getMessage());
        repository.findById(outboxId).ifPresent(row -> {
            final boolean atMaxAttempts = currentAttempt >= properties.maxAttempts();
            if (!failure.isRetryable() || atMaxAttempts) {
                giveUp(row, errorText);
                LOG.error("Outbox row gave up id={} attempts={} retryable={} error={}",
                    outboxId, currentAttempt, failure.isRetryable(), LogSanitizer.forLog(errorText));
            } else {
                final Duration backoff = currentAttempt == 1 ? FIRST_RETRY_BACKOFF : DEFAULT_BACKOFF;
                row.setNextAttemptAt(Instant.now().plus(backoff));
                row.setLastError(errorText);
                LOG.warn("Outbox row scheduled for retry id={} attempt={} backoff={}s",
                    outboxId, currentAttempt, backoff.toSeconds());
            }
            repository.save(row);
        });
    }

    /** A row that cannot render fails the same way on every retry, so it gives up at once. */
    @Transactional
    public void recordRenderFailure(final long outboxId, final IllegalStateException failure) {
        final String errorText = truncate(String.valueOf(failure.getMessage()));
        repository.findById(outboxId).ifPresent(row -> {
            giveUp(row, errorText);
            LOG.error("Outbox row could not render id={} error={}", outboxId, LogSanitizer.forLog(errorText));
            repository.save(row);
        });
    }

    private static void giveUp(final MailOutbox row, final String errorText) {
        row.setFailedAt(Instant.now());
        row.setLastError(errorText);
        row.setPayload(EMPTY_PAYLOAD);
    }

    private static String truncate(final String message) {
        return message.length() > ERROR_TEXT_MAX_LENGTH
            ? message.substring(0, ERROR_TEXT_MAX_LENGTH) : message;
    }
}
