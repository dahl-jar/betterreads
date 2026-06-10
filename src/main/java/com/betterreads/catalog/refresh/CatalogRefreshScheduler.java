package com.betterreads.catalog.refresh;

import java.util.concurrent.Executor;

import com.betterreads.common.scheduling.SkipIfRunningExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs {@link CatalogRefreshService#refreshKnownAuthorsAndSeries()} once a day on its own executor.
 *
 * <p>Runs at 02:00, before the 03:30 index reconcile, so a newly staged book is promoted and
 * indexed in the same night. The run is handed to a dedicated executor because re-resolving
 * thousands of authors takes hours; on the shared scheduler thread it delayed every later trigger
 * by that long. A run still in progress when the next trigger fires is skipped rather than queued.
 * Disabled by {@code betterreads.catalog.refresh.enabled=false}.
 */
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
        if (!executor.tryRun(refreshService::refreshKnownAuthorsAndSeries)) {
            LOG.info("catalog.refresh previous run still in progress, skipping this trigger");
        }
    }
}
