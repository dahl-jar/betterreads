package com.betterreads.scheduling;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Runs one job at a time on a delegate executor and skips a job submitted while another runs. */
public final class SkipIfRunningExecutor {

    private final Executor delegate;

    private final AtomicBoolean running = new AtomicBoolean();

    public SkipIfRunningExecutor(final Executor delegate) {
        this.delegate = delegate;
    }

    /**
     * Returns false without running the job when a previous job is still in progress.
     *
     * <p>A slot left taken would skip every later run until restart, so it is freed when the job
     * completes, throws, or is rejected by the delegate.
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
