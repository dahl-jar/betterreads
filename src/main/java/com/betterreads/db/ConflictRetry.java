package com.betterreads.db;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * Retries a write that loses a race for the same row.
 *
 * <p>Two concurrent first inserts collide on the unique constraint, and two concurrent updates
 * collide on the version check. The winner's row is readable on the next attempt. The write runs in
 * a separate proxied bean so each attempt gets a fresh transaction, since a retry inside the
 * rolled-back one fails again.
 */
public final class ConflictRetry {

    private ConflictRetry() {
    }

    /**
     * Rethrows the last conflict when every attempt loses. Other data access failures propagate on
     * the first try.
     *
     * @param maxAttempts total tries, including the first
     * @param event the log event id and any key=value context, logged once per retry
     */
    public static <T> T retryOnConflict(
        final int maxAttempts, final Logger log, final String event, final Supplier<T> write) {
        DataAccessException lastConflict = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return write.get();
            } catch (final DataIntegrityViolationException | OptimisticLockingFailureException conflict) {
                lastConflict = conflict;
                log.warn("{} attempt={}", event, attempt);
            }
        }
        throw lastConflict;
    }
}
