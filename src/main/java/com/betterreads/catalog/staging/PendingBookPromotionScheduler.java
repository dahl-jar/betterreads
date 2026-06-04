package com.betterreads.catalog.staging;

import com.betterreads.catalog.service.pipeline.PendingBookService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Triggers {@link PendingBookService#promoteReady()} on a schedule.
 *
 * <p>Lives in its own bean so the call goes through the Spring proxy; a self-call inside
 * {@code PendingBookService} would skip the proxy and lose the {@code @Transactional}.
 * Disabled by {@code betterreads.catalog.staging.poll-enabled=false}.
 */
@Component
class PendingBookPromotionScheduler {

    private final PendingBookService pendingBookService;

    private final PendingBookProperties properties;

    PendingBookPromotionScheduler(
        final PendingBookService pendingBookService,
        final PendingBookProperties properties
    ) {
        this.pendingBookService = pendingBookService;
        this.properties = properties;
    }

    /** Promotes complete candidates every five minutes. */
    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT5M")
    public void scheduledPromotion() {
        if (properties.pollEnabled()) {
            pendingBookService.promoteReady();
        }
    }
}
