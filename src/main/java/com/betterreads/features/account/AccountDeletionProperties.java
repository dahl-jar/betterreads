package com.betterreads.features.account;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "betterreads.auth.deletion")
record AccountDeletionProperties(
    long gracePeriodHours,
    boolean schedulerEnabled
) {

    /** 30 days. */
    private static final long DEFAULT_GRACE_PERIOD_HOURS = 720L;

    /**
     * Falls back to the default grace period when the configured value is non-positive, so a
     * misconfigured environment cannot hard-delete every soft-deleted user on the next sweep.
     */
    public AccountDeletionProperties {
        if (gracePeriodHours <= 0) {
            gracePeriodHours = DEFAULT_GRACE_PERIOD_HOURS;
        }
    }
}
