package com.betterreads.ratelimit;

import java.time.Duration;

/** Paces calls to a rate-limited resource. */
@FunctionalInterface
public interface RateLimiter {

    /** Waits up to {@code maxWait} for one permit and returns false when none frees in time. */
    boolean tryAcquire(Duration maxWait);
}
