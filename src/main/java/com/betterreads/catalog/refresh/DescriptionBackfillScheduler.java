package com.betterreads.catalog.refresh;

import java.util.concurrent.Executor;

import com.betterreads.common.scheduling.SkipIfRunningExecutor;
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

    private final SkipIfRunningExecutor executor;

    DescriptionBackfillScheduler(
        final DescriptionBackfillService backfillService,
        final DescriptionBackfillProperties properties,
        @Qualifier("descriptionBackfillExecutor") final Executor descriptionBackfillExecutor
    ) {
        this.backfillService = backfillService;
        this.properties = properties;
        this.executor = new SkipIfRunningExecutor(descriptionBackfillExecutor);
    }

    @Scheduled(cron = "0 0 4 * * *")
    public void scheduledBackfill() {
        if (!properties.enabled()) {
            return;
        }
        if (!executor.tryRun(this::runOnce)) {
            LOG.info("catalog.description-backfill previous run still in progress, skipping this trigger");
        }
    }

    private void runOnce() {
        if (properties.fullSweep()) {
            backfillService.fullSweep();
        } else {
            backfillService.backfillSlice();
        }
    }
}
