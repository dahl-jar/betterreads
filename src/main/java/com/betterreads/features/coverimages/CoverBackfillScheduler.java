package com.betterreads.features.coverimages;

import java.util.concurrent.Executor;

import com.betterreads.scheduling.SkipIfRunningExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the cover backfill once a day.
 *
 * <p>The 04:00 description backfill runs first, so an overnight-promoted book has its description
 * before its cover is mirrored.
 */
@Component
class CoverBackfillScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(CoverBackfillScheduler.class);

    private final CoverBackfillService backfillService;

    private final CoverBackfillProperties properties;

    private final SkipIfRunningExecutor executor;

    CoverBackfillScheduler(
        final CoverBackfillService backfillService,
        final CoverBackfillProperties properties,
        @Qualifier("coverBackfillExecutor") final Executor coverBackfillExecutor
    ) {
        this.backfillService = backfillService;
        this.properties = properties;
        this.executor = new SkipIfRunningExecutor(coverBackfillExecutor);
    }

    @Scheduled(cron = "0 30 4 * * *")
    public void scheduledBackfill() {
        if (!properties.enabled()) {
            return;
        }
        if (!executor.tryRun(this::runOnce)) {
            LOG.info("catalog.cover-backfill previous run still in progress, skipping this trigger");
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
