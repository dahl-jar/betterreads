package com.betterreads.catalog.refresh;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the cover backfill once a day on its own executor.
 *
 * <p>Runs at 04:30, after the 04:00 description backfill, so an overnight-promoted book has its
 * description filled before its cover is mirrored. The run is handed to a dedicated executor because
 * mirroring downloads and stores an image for every candidate; running it on the shared scheduler
 * thread would stall the mail outbox and the SSE heartbeat. A run still in progress when the next
 * trigger fires is skipped rather than queued. Disabled by
 * {@code betterreads.catalog.cover-backfill.enabled=false}.
 */
@Component
class CoverBackfillScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(CoverBackfillScheduler.class);

    private final CoverBackfillService backfillService;

    private final CoverBackfillProperties properties;

    private final Executor executor;

    private final AtomicBoolean running = new AtomicBoolean();

    CoverBackfillScheduler(
        final CoverBackfillService backfillService,
        final CoverBackfillProperties properties,
        @Qualifier("coverBackfillExecutor") final Executor coverBackfillExecutor
    ) {
        this.backfillService = backfillService;
        this.properties = properties;
        this.executor = coverBackfillExecutor;
    }

    @Scheduled(cron = "0 30 4 * * *")
    public void scheduledBackfill() {
        if (!properties.enabled()) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            LOG.info("catalog.cover-backfill previous run still in progress, skipping this trigger");
            return;
        }
        executor.execute(this::runOnce);
    }

    private void runOnce() {
        try {
            if (properties.fullSweep()) {
                backfillService.fullSweep();
            } else {
                backfillService.backfillSlice();
            }
        } finally {
            running.set(false);
        }
    }
}
