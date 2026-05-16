package com.betterreads.mail.outbox;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Claims pending outbox rows in its own transaction so the claim commits before the HTTP send.
 *
 * <p>Separate bean so the proxy applies; a self-call inside {@link MailOutboxWorker} would
 * skip {@code @Transactional}.
 */
@Component
class MailOutboxClaimer {

    private final MailOutboxRepository repository;

    private final MailOutboxProperties properties;

    MailOutboxClaimer(final MailOutboxRepository repository, final MailOutboxProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /**
     * Claims a batch of pending rows and returns their ids.
     *
     * <p>{@code next_attempt_at} is pushed forward so a crashed worker's claims become
     * eligible again after the timeout passes.
     */
    @Transactional
    public List<Long> claimBatch() {
        final Instant now = Instant.now();
        final List<MailOutbox> rows = repository.claimPending(now);
        final int batchSize = Math.min(rows.size(), properties.claimBatchSize());
        final List<MailOutbox> selected = rows.subList(0, batchSize);
        final Instant lease = now.plusSeconds(properties.leaseSeconds());
        for (final MailOutbox row : selected) {
            row.setAttemptCount(row.getAttemptCount() + 1);
            row.setNextAttemptAt(lease);
            repository.save(row);
        }
        return selected.stream().map(MailOutbox::getMailOutboxId).toList();
    }
}
