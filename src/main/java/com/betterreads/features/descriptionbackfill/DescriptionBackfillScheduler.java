package com.betterreads.features.descriptionbackfill;

import java.util.concurrent.Executor;

import com.betterreads.scheduling.SkipIfRunningExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the backfill at 04:00, after the 02:00 author refresh and the 03:30 index reconcile, so a book
 * promoted overnight is checked the same night.
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
