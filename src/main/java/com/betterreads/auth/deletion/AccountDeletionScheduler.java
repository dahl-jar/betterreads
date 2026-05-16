package com.betterreads.auth.deletion;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Triggers {@link AccountDeletionSweep#sweep()} on a schedule.
 *
 * <p>Lives in its own bean so the call goes through the Spring proxy; a self-call inside
 * {@code AccountDeletionSweep} would skip the proxy and lose the {@code @Transactional}.
 * Disabled by {@code betterreads.auth.deletion.scheduler-enabled=false}.
 */
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

    /** Runs the sweep once an hour. */
    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1H")
    public void scheduledSweep() {
        if (properties.schedulerEnabled()) {
            sweep.sweep();
        }
    }
}
