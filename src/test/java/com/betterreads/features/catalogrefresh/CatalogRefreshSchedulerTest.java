package com.betterreads.features.catalogrefresh;

import static org.mockito.Mockito.mock;
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
    @DisplayName("runs the refresh")
    void runsRefresh() {
        final CatalogRefreshScheduler scheduler = new CatalogRefreshScheduler(service, sameThread);

        scheduler.scheduledRefresh();

        verify(service).refresh();
    }

    @Test
    @DisplayName("does not start a second refresh while one is in progress")
    void skipsWhenAlreadyRunning() {
        final AtomicReference<CatalogRefreshScheduler> ref = new AtomicReference<>();
        final Executor reentrant = task -> {
            ref.get().scheduledRefresh();
            task.run();
        };
        final CatalogRefreshScheduler reentrantScheduler = new CatalogRefreshScheduler(service, reentrant);
        ref.set(reentrantScheduler);

        reentrantScheduler.scheduledRefresh();

        verify(service, times(1)).refresh();
    }
}
