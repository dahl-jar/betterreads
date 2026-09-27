package com.betterreads.features.descriptionbackfill;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DescriptionBackfillSchedulerTest {

    private final DescriptionBackfillService service = mock(DescriptionBackfillService.class);

    private final Executor sameThread = Runnable::run;

    @Test
    @DisplayName("runs the thin-only slice when enabled and not in full-sweep mode")
    void runsSlice() {
        final DescriptionBackfillScheduler scheduler =
            new DescriptionBackfillScheduler(service, new DescriptionBackfillProperties(true, false), sameThread);

        scheduler.scheduledBackfill();

        verify(service).backfillSlice();
        verify(service, never()).fullSweep();
    }

    @Test
    @DisplayName("runs the full sweep when the flag is set")
    void runsFullSweep() {
        final DescriptionBackfillScheduler scheduler =
            new DescriptionBackfillScheduler(service, new DescriptionBackfillProperties(true, true), sameThread);

        scheduler.scheduledBackfill();

        verify(service).fullSweep();
        verify(service, never()).backfillSlice();
    }

    @Test
    @DisplayName("does nothing when disabled")
    void skipsWhenDisabled() {
        final DescriptionBackfillScheduler scheduler =
            new DescriptionBackfillScheduler(service, new DescriptionBackfillProperties(false, false), sameThread);

        scheduler.scheduledBackfill();

        verify(service, never()).backfillSlice();
    }

    @Test
    @DisplayName("does not start a second run while one is in progress")
    void skipsWhenAlreadyRunning() {
        final AtomicReference<DescriptionBackfillScheduler> ref = new AtomicReference<>();
        final Executor reentrant = task -> {
            ref.get().scheduledBackfill();
            task.run();
        };
        final DescriptionBackfillScheduler reentrantScheduler =
            new DescriptionBackfillScheduler(service, new DescriptionBackfillProperties(true, false), reentrant);
        ref.set(reentrantScheduler);

        reentrantScheduler.scheduledBackfill();

        verify(service, times(1)).backfillSlice();
    }
}
