package com.betterreads.features.bookstaging;

import java.util.concurrent.Executor;

import com.betterreads.scheduling.SkipIfRunningExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the staging promotion poll on a schedule.
 *
 * <p>Draining a promotion backlog takes hours, and on the shared scheduler thread it delayed every
 * nightly cron job by that long, so the poll gets its own executor.
 */
@Component
class PendingBookPromotionScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(PendingBookPromotionScheduler.class);

    private final PendingBookService pendingBookService;

    private final PendingBookProperties properties;

    private final SkipIfRunningExecutor executor;

    PendingBookPromotionScheduler(
        final PendingBookService pendingBookService,
        final PendingBookProperties properties,
        @Qualifier("promotionPollExecutor") final Executor promotionPollExecutor
    ) {
        this.pendingBookService = pendingBookService;
        this.properties = properties;
        this.executor = new SkipIfRunningExecutor(promotionPollExecutor);
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT5M")
    public void scheduledPromotion() {
        if (!properties.pollEnabled()) {
            return;
        }
        if (!executor.tryRun(pendingBookService::promoteReady)) {
            LOG.info("catalog.staging previous promotion poll still in progress, skipping this trigger");
        }
    }
}
