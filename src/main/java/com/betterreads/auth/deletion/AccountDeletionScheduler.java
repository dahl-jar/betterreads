package com.betterreads.auth.deletion;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Hourly trigger for the account-deletion sweep. Lives in its own bean so the {@code @Scheduled}
 * call into {@link AccountDeletionSweep#sweep()} goes through the Spring proxy; a self-call
 * inside {@code AccountDeletionSweep} would skip the proxy and the {@code @Transactional} on
 * {@code sweep()} would not apply.
 *
 * <p>Disabled by setting {@code betterreads.auth.deletion.scheduler-enabled=false}, which the
 * integration tests use so they can call {@link AccountDeletionSweep#sweep()} directly without
 * racing the scheduler.
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

    /**
     * Hourly sweep with a one-hour {@code initialDelay} so the first invocation never fires
     * during integration-test startup (which runs the Spring context for seconds, not hours).
     * Tests that exercise the sweep call {@link AccountDeletionSweep#sweep()} directly.
     */
    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1H")
    public void scheduledSweep() {
        if (properties.schedulerEnabled()) {
            sweep.sweep();
        }
    }
}
