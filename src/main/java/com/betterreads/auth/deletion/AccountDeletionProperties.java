package com.betterreads.auth.deletion;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Account-deletion configuration bound from {@code betterreads.auth.deletion.*}.
 *
 * @param gracePeriodHours hours a soft-deleted user is kept before hard deletion (default 720)
 * @param schedulerEnabled whether the scheduled sweep runs automatically
 */
@ConfigurationProperties(prefix = "betterreads.auth.deletion")
public record AccountDeletionProperties(
    long gracePeriodHours,
    boolean schedulerEnabled
) {

    /** Default grace period: 30 days. */
    public static final long DEFAULT_GRACE_PERIOD_HOURS = 720L;

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
