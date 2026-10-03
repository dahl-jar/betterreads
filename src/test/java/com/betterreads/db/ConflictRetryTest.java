package com.betterreads.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.dao.OptimisticLockingFailureException;

final class ConflictRetryTest {

    private static final Logger LOG = LoggerFactory.getLogger(ConflictRetryTest.class);

    private static final String EVENT = "shelf.upsert.retry";

    private static final int MAX_ATTEMPTS = 3;

    private static final String WINNER = "winner";

    private final AtomicInteger calls = new AtomicInteger();

    @Test
    void shouldReturnSecondAttemptAfterConflict() {
        final String result = ConflictRetry.retryOnConflict(MAX_ATTEMPTS, LOG, EVENT, () -> {
            if (calls.incrementAndGet() == 1) {
                throw new DataIntegrityViolationException("duplicate shelf row");
            }
            return WINNER;
        });

        assertThat(result).isEqualTo(WINNER);
        assertThat(calls).hasValue(2);
    }

    @Test
    void shouldRethrowLastConflictWhenEveryAttemptLoses() {
        final List<DataAccessException> conflicts = List.of(
                new DataIntegrityViolationException("first"),
                new OptimisticLockingFailureException("second"),
                new OptimisticLockingFailureException("third"));

        final Throwable thrown = catchThrowable(() -> ConflictRetry.retryOnConflict(MAX_ATTEMPTS, LOG, EVENT, () -> {
            throw conflicts.get(calls.getAndIncrement());
        }));

        assertThat(thrown).isSameAs(conflicts.get(MAX_ATTEMPTS - 1));
        assertThat(calls).hasValue(MAX_ATTEMPTS);
    }

    @Test
    void shouldNotRetryOtherDataAccessFailure() {
        final DataRetrievalFailureException failure = new DataRetrievalFailureException("connection lost");

        final Throwable thrown = catchThrowable(() -> ConflictRetry.retryOnConflict(MAX_ATTEMPTS, LOG, EVENT, () -> {
            calls.incrementAndGet();
            throw failure;
        }));

        assertThat(thrown).isSameAs(failure);
        assertThat(calls).hasValue(1);
    }
}
