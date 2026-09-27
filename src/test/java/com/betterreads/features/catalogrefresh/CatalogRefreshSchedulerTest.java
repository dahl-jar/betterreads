package com.betterreads.features.catalogrefresh;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CatalogRefreshSchedulerTest {

    private final CatalogRefreshService service = mock(CatalogRefreshService.class);

    private final Executor sameThread = Runnable::run;

    @Test
    @DisplayName("runs the refresh when enabled")
    void runsRefresh() {
        final CatalogRefreshScheduler scheduler = scheduler(true, sameThread);

        scheduler.scheduledRefresh();

        verify(service).refresh();
    }

    @Test
    @DisplayName("does nothing when disabled")
    void skipsWhenDisabled() {
        final CatalogRefreshScheduler scheduler = scheduler(false, sameThread);

        scheduler.scheduledRefresh();

        verify(service, never()).refresh();
    }

    @Test
    @DisplayName("does not start a second refresh while one is in progress")
    void skipsWhenAlreadyRunning() {
        final AtomicReference<CatalogRefreshScheduler> ref = new AtomicReference<>();
        final Executor reentrant = task -> {
            ref.get().scheduledRefresh();
            task.run();
        };
        final CatalogRefreshScheduler reentrantScheduler = scheduler(true, reentrant);
        ref.set(reentrantScheduler);

        reentrantScheduler.scheduledRefresh();

        verify(service, times(1)).refresh();
    }

    private CatalogRefreshScheduler scheduler(final boolean enabled, final Executor executor) {
        return new CatalogRefreshScheduler(service, new CatalogRefreshProperties(enabled), executor);
    }
}
