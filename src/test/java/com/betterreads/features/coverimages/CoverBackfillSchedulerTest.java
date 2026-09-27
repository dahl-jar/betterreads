package com.betterreads.features.coverimages;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CoverBackfillSchedulerTest {

    private final CoverBackfillService service = mock(CoverBackfillService.class);

    private final Executor sameThread = Runnable::run;

    @Test
    @DisplayName("runs the slice when enabled and not in full-sweep mode")
    void runsSlice() {
        final CoverBackfillScheduler scheduler =
            new CoverBackfillScheduler(service, new CoverBackfillProperties(true, false), sameThread);

        scheduler.scheduledBackfill();

        verify(service).backfillSlice();
        verify(service, never()).fullSweep();
    }

    @Test
    @DisplayName("runs the full sweep when the flag is set")
    void runsFullSweep() {
        final CoverBackfillScheduler scheduler =
            new CoverBackfillScheduler(service, new CoverBackfillProperties(true, true), sameThread);

        scheduler.scheduledBackfill();

        verify(service).fullSweep();
        verify(service, never()).backfillSlice();
    }

    @Test
    @DisplayName("does nothing when disabled")
    void skipsWhenDisabled() {
        final CoverBackfillScheduler scheduler =
            new CoverBackfillScheduler(service, new CoverBackfillProperties(false, false), sameThread);

        scheduler.scheduledBackfill();

        verify(service, never()).backfillSlice();
    }

    @Test
    @DisplayName("does not start a second run while one is in progress")
    void skipsWhenAlreadyRunning() {
        final AtomicReference<CoverBackfillScheduler> ref = new AtomicReference<>();
        final Executor reentrant = task -> {
            ref.get().scheduledBackfill();
            task.run();
        };
        final CoverBackfillScheduler reentrantScheduler =
            new CoverBackfillScheduler(service, new CoverBackfillProperties(true, false), reentrant);
        ref.set(reentrantScheduler);

        reentrantScheduler.scheduledBackfill();

        verify(service, times(1)).backfillSlice();
    }
}
