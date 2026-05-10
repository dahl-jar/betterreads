package com.betterreads.auth.deletion;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Account-deletion configuration bound from {@code betterreads.auth.deletion.*}.
 *
 * @param gracePeriodHours how long a soft-deleted user remains in {@code app_user} before the
 *     sweep removes the row. 720 hours (30 days) by default; tests override to a smaller value.
 * @param schedulerEnabled whether the scheduled sweep fires automatically. Production runs with
 *     {@code true}; integration tests disable it and call
 *     {@link AccountDeletionSweep#sweep()} directly so timing stays deterministic.
 */
@ConfigurationProperties(prefix = "betterreads.auth.deletion")
public record AccountDeletionProperties(
    long gracePeriodHours,
    boolean schedulerEnabled
) {

    /** Default grace period: 30 days. */
    public static final long DEFAULT_GRACE_PERIOD_HOURS = 720L;

    /**
     * Replaces a non-positive {@code gracePeriodHours} with {@link #DEFAULT_GRACE_PERIOD_HOURS}
     * so a misconfigured environment cannot accidentally hard-delete every soft-deleted user
     * on the next sweep tick.
     */
    public AccountDeletionProperties {
        if (gracePeriodHours <= 0) {
            gracePeriodHours = DEFAULT_GRACE_PERIOD_HOURS;
        }
    }
}
