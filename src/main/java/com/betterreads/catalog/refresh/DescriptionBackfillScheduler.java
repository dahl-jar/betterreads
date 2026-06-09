package com.betterreads.catalog.refresh;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the description backfill once a day on its own executor.
 *
 * <p>Runs at 04:00, after the 02:00 author and series refresh and the 03:30 index reconcile, so a
 * book promoted overnight is a candidate the same night. The run is handed to a dedicated executor
 * because a slice makes rate-limited iTunes calls for up to 50 books and can take minutes; running it
 * on the shared scheduler thread would stall the mail outbox and the SSE heartbeat. A run still in
 * progress when the next trigger fires is skipped rather than queued. Disabled by
 * {@code betterreads.catalog.description-backfill.enabled=false}.
 */
@Component
class DescriptionBackfillScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(DescriptionBackfillScheduler.class);

    private final DescriptionBackfillService backfillService;

    private final DescriptionBackfillProperties properties;

    private final Executor executor;

    private final AtomicBoolean running = new AtomicBoolean();

    DescriptionBackfillScheduler(
        final DescriptionBackfillService backfillService,
        final DescriptionBackfillProperties properties,
        @Qualifier("descriptionBackfillExecutor") final Executor descriptionBackfillExecutor
    ) {
        this.backfillService = backfillService;
        this.properties = properties;
        this.executor = descriptionBackfillExecutor;
    }

    @Scheduled(cron = "0 0 4 * * *")
    public void scheduledBackfill() {
        if (!properties.enabled()) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            LOG.info("catalog.description-backfill previous run still in progress, skipping this trigger");
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
