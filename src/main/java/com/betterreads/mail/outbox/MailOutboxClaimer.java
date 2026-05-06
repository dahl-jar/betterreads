package com.betterreads.mail.outbox;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Claims pending outbox rows in their own transaction so the lock + attempt-count update
 * commits before the worker's HTTP send. Lives in a sibling bean so the proxy boundary
 * actually applies; a self-call inside {@link MailOutboxWorker} would bypass {@code @Transactional}.
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
     * Returns the ids of rows freshly claimed for delivery. Each claimed row's
     * {@code attempt_count} is incremented and {@code next_attempt_at} bumped by the configured
     * lease so a crashed worker's claims free up after the lease expires.
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
