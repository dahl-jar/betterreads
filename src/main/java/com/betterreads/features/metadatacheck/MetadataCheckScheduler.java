package com.betterreads.features.metadatacheck;

import java.util.concurrent.Executor;

import com.betterreads.scheduling.SkipIfRunningExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class MetadataCheckScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(MetadataCheckScheduler.class);

    private final MetadataCheckService service;

    private final MetadataCheckProperties properties;

    private final SkipIfRunningExecutor executor;

    MetadataCheckScheduler(
        final MetadataCheckService service,
        final MetadataCheckProperties properties,
        @Qualifier("metadataCheckExecutor") final Executor metadataCheckExecutor
    ) {
        this.service = service;
        this.properties = properties;
        this.executor = new SkipIfRunningExecutor(metadataCheckExecutor);
    }

    @Scheduled(cron = "0 0 5 * * *")
    public void scheduledCheck() {
        if (!properties.enabled()) {
            return;
        }
        if (!executor.tryRun(service::checkNewBooks)) {
            LOG.info("catalog.metadata-check previous run still in progress, skipping this trigger");
        }
    }
}
