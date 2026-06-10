package com.betterreads.common.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The executor runs one job at a time, skips a job submitted mid-run, and frees the slot after a
 * completed, failed, or rejected run.
 */
class SkipIfRunningExecutorTest {

    private final Executor sameThread = Runnable::run;

    @Test
    @DisplayName("skips a job submitted while one is running")
    void skipsJobSubmittedWhileRunning() {
        final SkipIfRunningExecutor executor = new SkipIfRunningExecutor(sameThread);
        final AtomicBoolean innerStarted = new AtomicBoolean();
        final Runnable resubmitting = () -> innerStarted.set(executor.tryRun(() -> { }));

        final boolean outerStarted = executor.tryRun(resubmitting);

        assertThat(outerStarted).isTrue();
        assertThat(innerStarted).isFalse();
    }

    @Test
    @DisplayName("runs a second job after the first completes")
    void runsSecondJobAfterFirstCompletes() {
        final SkipIfRunningExecutor executor = new SkipIfRunningExecutor(sameThread);
        final AtomicInteger runs = new AtomicInteger();

        executor.tryRun(runs::incrementAndGet);
        final boolean secondStarted = executor.tryRun(runs::incrementAndGet);

        assertThat(secondStarted).isTrue();
        assertThat(runs).hasValue(2);
    }

    @Test
    @DisplayName("runs a second job after the first throws")
    void runsSecondJobAfterFirstThrows() {
        final SkipIfRunningExecutor executor = new SkipIfRunningExecutor(sameThread);
        final Runnable failing = () -> {
            throw new IllegalStateException("job failed");
        };

        assertThatThrownBy(() -> executor.tryRun(failing)).isInstanceOf(IllegalStateException.class);

        final AtomicBoolean secondRan = new AtomicBoolean();
        executor.tryRun(() -> secondRan.set(true));
        assertThat(secondRan).isTrue();
    }

    @Test
    @DisplayName("runs a second job after the delegate rejects the first")
    void runsSecondJobAfterDelegateRejectsFirst() {
        final AtomicInteger submissions = new AtomicInteger();
        final Executor rejectingFirst = task -> {
            if (submissions.incrementAndGet() == 1) {
                throw new RejectedExecutionException("queue full");
            }
            task.run();
        };
        final SkipIfRunningExecutor executor = new SkipIfRunningExecutor(rejectingFirst);

        assertThatThrownBy(() -> executor.tryRun(() -> { }))
            .isInstanceOf(RejectedExecutionException.class);

        final AtomicBoolean secondRan = new AtomicBoolean();
        executor.tryRun(() -> secondRan.set(true));
        assertThat(secondRan).isTrue();
    }
}
