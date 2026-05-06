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

/**
 * Persistence access for {@link MailOutbox}. The claim query uses pessimistic locking with
 * {@code SKIP LOCKED} so multiple workers can run concurrently without competing for the same
 * row.
 */
@Repository
public interface MailOutboxRepository extends JpaRepository<MailOutbox, Long> {

    /**
     * Returns up to {@code limit} pending outbox rows whose {@code next_attempt_at} has elapsed,
     * locking each row {@code FOR UPDATE SKIP LOCKED} so concurrent workers grab disjoint sets.
     *
     * <p>Caller must update the row state (claim/sent/failed) and commit before any other
     * worker can see the row. Hibernate emits the lock hint as Postgres-native
     * {@code SELECT ... FOR UPDATE SKIP LOCKED}.
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
