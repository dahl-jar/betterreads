package com.betterreads.catalog.staging;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import com.betterreads.catalog.service.pipeline.PendingBookService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The scheduler runs the promotion poll on a separate executor, skips when disabled, and does not
 * start a second poll while one is in progress.
 */
class PendingBookPromotionSchedulerTest {

    private final PendingBookService service = mock(PendingBookService.class);

    private final PendingBookProperties properties = mock(PendingBookProperties.class);

    private final Executor sameThread = Runnable::run;

    private final PendingBookPromotionScheduler scheduler =
        new PendingBookPromotionScheduler(service, properties, sameThread);

    @Test
    @DisplayName("runs the promotion poll when enabled")
    void runsPromotionPoll() {
        when(properties.pollEnabled()).thenReturn(true);

        scheduler.scheduledPromotion();

        verify(service).promoteReady();
    }

    @Test
    @DisplayName("does nothing when disabled")
    void skipsWhenDisabled() {
        when(properties.pollEnabled()).thenReturn(false);

        scheduler.scheduledPromotion();

        verify(service, never()).promoteReady();
    }

    @Test
    @DisplayName("does not start a second poll while one is in progress")
    void skipsWhenAlreadyRunning() {
        when(properties.pollEnabled()).thenReturn(true);
        final AtomicReference<PendingBookPromotionScheduler> ref = new AtomicReference<>();
        final Executor reentrant = task -> {
            ref.get().scheduledPromotion();
            task.run();
        };
        final PendingBookPromotionScheduler reentrantScheduler =
            new PendingBookPromotionScheduler(service, properties, reentrant);
        ref.set(reentrantScheduler);

        reentrantScheduler.scheduledPromotion();

        verify(service, times(1)).promoteReady();
    }
}
