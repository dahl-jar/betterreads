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
 * The scheduler runs the backfill on a separate executor, picks slice or full-sweep mode, skips when
 * disabled, and does not start a second run while one is in progress.
 */
class DescriptionBackfillSchedulerTest {

    private final DescriptionBackfillService service = mock(DescriptionBackfillService.class);

    private final DescriptionBackfillProperties properties = mock(DescriptionBackfillProperties.class);

    private final Executor sameThread = Runnable::run;

    private final DescriptionBackfillScheduler scheduler =
        new DescriptionBackfillScheduler(service, properties, sameThread);

    @Test
    @DisplayName("runs the thin-only slice when enabled and not in full-sweep mode")
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
        final AtomicReference<DescriptionBackfillScheduler> ref = new AtomicReference<>();
        final Executor reentrant = task -> {
            ref.get().scheduledBackfill();
            task.run();
        };
        final DescriptionBackfillScheduler reentrantScheduler =
            new DescriptionBackfillScheduler(service, properties, reentrant);
        ref.set(reentrantScheduler);

        reentrantScheduler.scheduledBackfill();

        verify(service, times(1)).backfillSlice();
    }
}
