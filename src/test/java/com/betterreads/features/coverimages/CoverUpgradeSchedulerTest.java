package com.betterreads.features.coverimages;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.Test;

class CoverUpgradeSchedulerTest {

    private static final int BATCH = 500;

    private static final Duration SEARCH_AGAIN = Duration.ofDays(30);

    private final CoverUpgradeJob job = mock(CoverUpgradeJob.class);

    private final Executor sameThread = Runnable::run;

    @Test
    void shouldRunTheJobWhenEnabled() {
        final CoverUpgradeScheduler scheduler = scheduler(true);

        scheduler.scheduledUpgrade();

        verify(job).upgrade();
    }

    @Test
    void shouldStayIdleWhenDisabled() {
        final CoverUpgradeScheduler scheduler = scheduler(false);

        scheduler.scheduledUpgrade();

        verify(job, never()).upgrade();
    }

    private CoverUpgradeScheduler scheduler(final boolean enabled) {
        return new CoverUpgradeScheduler(
            job, new CoverUpgradeProperties(enabled, BATCH, SEARCH_AGAIN), sameThread);
    }
}
