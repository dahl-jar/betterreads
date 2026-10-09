package com.betterreads.features.coverimages;

import java.util.concurrent.Executor;

import com.betterreads.scheduling.SkipIfRunningExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class CoverUpgradeScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(CoverUpgradeScheduler.class);

    private final CoverUpgradeJob job;

    private final CoverUpgradeProperties properties;

    private final SkipIfRunningExecutor executor;

    CoverUpgradeScheduler(
        final CoverUpgradeJob job,
        final CoverUpgradeProperties properties,
        @Qualifier("coverUpgradeExecutor") final Executor coverUpgradeExecutor
    ) {
        this.job = job;
        this.properties = properties;
        this.executor = new SkipIfRunningExecutor(coverUpgradeExecutor);
    }

    @Scheduled(cron = "${betterreads.catalog.cover-upgrade.cron}")
    public void scheduledUpgrade() {
        if (!properties.enabled()) {
            return;
        }
        if (!executor.tryRun(job::upgrade)) {
            LOG.info("catalog.cover-upgrade previous run still in progress, skipping this trigger");
        }
    }
}
