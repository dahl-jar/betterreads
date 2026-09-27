package com.betterreads.features.catalogrefresh;

import java.util.concurrent.Executor;

import com.betterreads.scheduling.SkipIfRunningExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** runs before the nightly index reconcile, so a newly staged book is indexed the same night */
@Component
class CatalogRefreshScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(CatalogRefreshScheduler.class);

    private final CatalogRefreshService refreshService;

    private final CatalogRefreshProperties properties;

    private final SkipIfRunningExecutor executor;

    CatalogRefreshScheduler(
        final CatalogRefreshService refreshService,
        final CatalogRefreshProperties properties,
        @Qualifier("catalogRefreshExecutor") final Executor catalogRefreshExecutor
    ) {
        this.refreshService = refreshService;
        this.properties = properties;
        this.executor = new SkipIfRunningExecutor(catalogRefreshExecutor);
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void scheduledRefresh() {
        if (!properties.enabled()) {
            return;
        }
        if (!executor.tryRun(refreshService::refresh)) {
            LOG.info("catalog.refresh previous run still in progress, skipping this trigger");
        }
    }
}
