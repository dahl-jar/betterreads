package com.betterreads.common.scheduling;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs at most one job at a time on a delegate executor; a job submitted while one is running is
 * skipped rather than queued.
 */
public final class SkipIfRunningExecutor {

    private final Executor delegate;

    private final AtomicBoolean running = new AtomicBoolean();

    public SkipIfRunningExecutor(final Executor delegate) {
        this.delegate = delegate;
    }

    /**
     * Runs the job on the delegate and returns {@code true}, or returns {@code false} without running
     * it when a previous job is still in progress.
     *
     * <p>The slot is freed when the job completes, throws, or is rejected by the delegate; an unfreed
     * slot would skip every later run until restart.
     */
    public boolean tryRun(final Runnable job) {
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        try {
            delegate.execute(() -> {
                try {
                    job.run();
                } finally {
                    running.set(false);
                }
            });
        } catch (RejectedExecutionException ex) {
            running.set(false);
            throw ex;
        }
        return true;
    }
}
