package com.betterreads.mail.outbox;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Persistence access for {@link MailOutbox}. */
@Repository
public interface MailOutboxRepository extends JpaRepository<MailOutbox, Long> {

    /**
     * Returns pending rows whose {@code next_attempt_at} has elapsed, locked
     * {@code FOR UPDATE SKIP LOCKED} so two workers never claim the same row.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
        SELECT m FROM MailOutbox m
        WHERE m.sentAt IS NULL
          AND m.failedAt IS NULL
          AND m.nextAttemptAt <= :now
        ORDER BY m.nextAttemptAt ASC
        """)
    List<MailOutbox> claimPending(@Param("now") Instant now);
}
