package com.betterreads.features.account;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class AccountDeletionScheduler {

    private final AccountDeletionSweep sweep;

    private final AccountDeletionProperties properties;

    AccountDeletionScheduler(
        final AccountDeletionSweep sweep,
        final AccountDeletionProperties properties
    ) {
        this.sweep = sweep;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1H")
    public void scheduledSweep() {
        if (properties.schedulerEnabled()) {
            sweep.sweep();
        }
    }
}
