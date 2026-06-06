package com.betterreads.catalog.refresh;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs {@link CatalogRefreshService#refreshKnownAuthorsAndSeries()} once a day.
 *
 * <p>Lives in its own bean so the call goes through the Spring proxy and keeps the service's
 * {@code @Transactional}. Runs at 02:00, before the 03:30 index reconcile, so a newly staged book is
 * promoted and indexed in the same night. Disabled by {@code betterreads.catalog.refresh.enabled=false}.
 */
@Component
class CatalogRefreshScheduler {

    private final CatalogRefreshService refreshService;

    private final CatalogRefreshProperties properties;

    CatalogRefreshScheduler(
        final CatalogRefreshService refreshService,
        final CatalogRefreshProperties properties
    ) {
        this.refreshService = refreshService;
        this.properties = properties;
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void scheduledRefresh() {
        if (properties.enabled()) {
            refreshService.refreshKnownAuthorsAndSeries();
        }
    }
}
