package com.betterreads.features.bookstaging;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PendingBookPromotionSchedulerTest {

    private final PendingBookService service = mock(PendingBookService.class);

    private final Executor sameThread = Runnable::run;

    @Test
    @DisplayName("runs the promotion poll when enabled")
    void runsPromotionPoll() {
        final PendingBookPromotionScheduler scheduler = scheduler(true);

        scheduler.scheduledPromotion();

        verify(service).promoteReady();
    }

    @Test
    @DisplayName("does nothing when disabled")
    void skipsWhenDisabled() {
        final PendingBookPromotionScheduler scheduler = scheduler(false);

        scheduler.scheduledPromotion();

        verify(service, never()).promoteReady();
    }

    @Test
    @DisplayName("does not start a second poll while one is in progress")
    void skipsWhenAlreadyRunning() {
        final AtomicReference<PendingBookPromotionScheduler> ref = new AtomicReference<>();
        final Executor reentrant = task -> {
            ref.get().scheduledPromotion();
            task.run();
        };
        final PendingBookPromotionScheduler reentrantScheduler =
            new PendingBookPromotionScheduler(service, new PendingBookProperties(true), reentrant);
        ref.set(reentrantScheduler);

        reentrantScheduler.scheduledPromotion();

        verify(service, times(1)).promoteReady();
    }

    private PendingBookPromotionScheduler scheduler(final boolean pollEnabled) {
        return new PendingBookPromotionScheduler(service, new PendingBookProperties(pollEnabled), sameThread);
    }
}
