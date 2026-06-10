package com.betterreads.catalog.staging;

import java.util.concurrent.Executor;

import com.betterreads.catalog.service.pipeline.PendingBookService;
import com.betterreads.common.scheduling.SkipIfRunningExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Triggers {@link PendingBookService#promoteReady()} on a schedule.
 *
 * <p>Lives in its own bean so the call goes through the Spring proxy; a self-call inside
 * {@code PendingBookService} would skip the proxy and lose the {@code @Transactional}. The poll runs
 * on a dedicated executor because draining a promotion backlog takes hours; on the shared scheduler
 * thread it delayed every nightly cron job by that long. A poll still in progress when the next
 * trigger fires is skipped rather than queued. Disabled by
 * {@code betterreads.catalog.staging.poll-enabled=false}.
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

    /** Promotes complete candidates every five minutes. */
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
