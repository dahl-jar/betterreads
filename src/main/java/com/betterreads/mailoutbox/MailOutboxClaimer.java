package com.betterreads.mailoutbox;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Claims pending outbox rows in its own transaction, so the claim commits before the HTTP send.
 * A self-call from the worker would skip {@code @Transactional}, so this is a separate bean.
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
     * A worker can crash mid-send, so {@code next_attempt_at} moves to the lease end and the
     * row is claimable again after it.
     */
    @Transactional
    public List<Long> claimBatch() {
        final Instant now = Instant.now();
        final List<MailOutbox> rows = repository.claimPending(now, Limit.of(properties.claimBatchSize()));
        final Instant lease = now.plusSeconds(properties.leaseSeconds());
        for (final MailOutbox row : rows) {
            row.setAttemptCount(row.getAttemptCount() + 1);
            row.setNextAttemptAt(lease);
            repository.save(row);
        }
        return rows.stream().map(MailOutbox::getMailOutboxId).toList();
    }
}
