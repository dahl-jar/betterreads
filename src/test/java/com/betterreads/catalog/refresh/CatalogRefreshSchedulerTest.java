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
 * The scheduler runs the refresh on a separate executor, skips when disabled, and does not start a
 * second refresh while one is in progress.
 */
class CatalogRefreshSchedulerTest {

    private final CatalogRefreshService service = mock(CatalogRefreshService.class);

    private final CatalogRefreshProperties properties = mock(CatalogRefreshProperties.class);

    private final Executor sameThread = Runnable::run;

    private final CatalogRefreshScheduler scheduler =
        new CatalogRefreshScheduler(service, properties, sameThread);

    @Test
    @DisplayName("runs the refresh when enabled")
    void runsRefresh() {
        when(properties.enabled()).thenReturn(true);

        scheduler.scheduledRefresh();

        verify(service).refreshKnownAuthorsAndSeries();
    }

    @Test
    @DisplayName("does nothing when disabled")
    void skipsWhenDisabled() {
        when(properties.enabled()).thenReturn(false);

        scheduler.scheduledRefresh();

        verify(service, never()).refreshKnownAuthorsAndSeries();
    }

    @Test
    @DisplayName("does not start a second refresh while one is in progress")
    void skipsWhenAlreadyRunning() {
        when(properties.enabled()).thenReturn(true);
        final AtomicReference<CatalogRefreshScheduler> ref = new AtomicReference<>();
        final Executor reentrant = task -> {
            ref.get().scheduledRefresh();
            task.run();
        };
        final CatalogRefreshScheduler reentrantScheduler =
            new CatalogRefreshScheduler(service, properties, reentrant);
        ref.set(reentrantScheduler);

        reentrantScheduler.scheduledRefresh();

        verify(service, times(1)).refreshKnownAuthorsAndSeries();
    }
}
