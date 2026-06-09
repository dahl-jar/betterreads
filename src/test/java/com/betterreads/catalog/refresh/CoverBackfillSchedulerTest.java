package com.betterreads.catalog.refresh;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The scheduler runs the cover backfill on a separate executor, picks slice or full-sweep mode,
 * skips when disabled, and does not start a second run while one is in progress.
 */
class CoverBackfillSchedulerTest {

    private final CoverBackfillService service = mock(CoverBackfillService.class);

    private final CoverBackfillProperties properties = mock(CoverBackfillProperties.class);

    private final Executor sameThread = Runnable::run;

    private final CoverBackfillScheduler scheduler =
        new CoverBackfillScheduler(service, properties, sameThread);

    @Test
    @DisplayName("runs the slice when enabled and not in full-sweep mode")
    void runsSlice() {
        when(properties.enabled()).thenReturn(true);
        when(properties.fullSweep()).thenReturn(false);

        scheduler.scheduledBackfill();

        verify(service).backfillSlice();
        verify(service, never()).fullSweep();
    }

    @Test
    @DisplayName("runs the full sweep when the flag is set")
    void runsFullSweep() {
        when(properties.enabled()).thenReturn(true);
        when(properties.fullSweep()).thenReturn(true);

        scheduler.scheduledBackfill();

        verify(service).fullSweep();
        verify(service, never()).backfillSlice();
    }

    @Test
    @DisplayName("does nothing when disabled")
    void skipsWhenDisabled() {
        when(properties.enabled()).thenReturn(false);

        scheduler.scheduledBackfill();

        verify(service, never()).backfillSlice();
    }

    @Test
    @DisplayName("does not start a second run while one is in progress")
    void skipsWhenAlreadyRunning() {
        when(properties.enabled()).thenReturn(true);
        when(properties.fullSweep()).thenReturn(false);
        final AtomicReference<CoverBackfillScheduler> ref = new AtomicReference<>();
        final Executor reentrant = task -> {
            ref.get().scheduledBackfill();
            task.run();
        };
        final CoverBackfillScheduler reentrantScheduler =
            new CoverBackfillScheduler(service, properties, reentrant);
        ref.set(reentrantScheduler);

        reentrantScheduler.scheduledBackfill();

        verify(service, times(1)).backfillSlice();
    }
}
