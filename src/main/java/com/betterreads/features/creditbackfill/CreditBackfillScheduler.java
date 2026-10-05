package com.betterreads.features.creditbackfill;

import java.util.concurrent.Executor;

import com.betterreads.scheduling.SkipIfRunningExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class CreditBackfillScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(CreditBackfillScheduler.class);

    private final CreditBackfillService backfillService;

    private final CreditBackfillProperties properties;

    private final SkipIfRunningExecutor executor;

    CreditBackfillScheduler(
        final CreditBackfillService backfillService,
        final CreditBackfillProperties properties,
        @Qualifier("creditBackfillExecutor") final Executor creditBackfillExecutor
    ) {
        this.backfillService = backfillService;
        this.properties = properties;
        this.executor = new SkipIfRunningExecutor(creditBackfillExecutor);
    }

    @Scheduled(cron = "${betterreads.catalog.credit-backfill.cron:0 * * * * *}")
    public void scheduledBackfill() {
        if (!properties.enabled()) {
            return;
        }
        if (!executor.tryRun(backfillService::backfillSlice)) {
            LOG.info("catalog.credit-backfill previous run still in progress, skipping this trigger");
        }
    }
}
