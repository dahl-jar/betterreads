package com.betterreads.common.ratelimit;

import java.time.Duration;

/**
 * Paces calls to a rate-limited resource.
 *
 * <p>A caller asks for one permit, waiting up to a bound for one to free.
 */
@FunctionalInterface
public interface RateLimiter {

    /**
     * Tries to take one permit, waiting up to {@code maxWait} for one to free.
     *
     * @return true when a permit was taken, false when none freed in time
     */
    boolean tryAcquire(Duration maxWait);
}
