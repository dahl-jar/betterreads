package com.betterreads.common.util;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * Retries a write that loses a race for the same row. Two concurrent first writes both miss the
 * pre-read and insert, and the loser hits the unique constraint; two concurrent updates to the same
 * row collide on the version check. Either way the winner's row is there to read on the next attempt.
 *
 * <p>The write must run in a separate proxied bean so each attempt gets a fresh transaction: the
 * failure rolls the previous one back, and a retry inside the rolled-back transaction fails again.
 */
public final class ConflictRetry {

    private ConflictRetry() {
    }

    /**
     * Runs {@code write} up to {@code maxAttempts} times, retrying only the two race failures above
     * and rethrowing the last one if every attempt loses. Any other {@link DataAccessException}
     * propagates on the first try, since no retry would fix it.
     *
     * @param maxAttempts the total number of tries, including the first
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
