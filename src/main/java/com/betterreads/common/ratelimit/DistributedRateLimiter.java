package com.betterreads.common.ratelimit;

import java.time.Duration;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;

/**
 * A {@link RateLimiter} backed by a Redis-stored Bucket4j bucket, so the limit holds across every
 * replica that shares the proxy manager.
 *
 * <p>One bucket per key with a per-minute refill, fetched from the proxy manager on each call.
 */
public final class DistributedRateLimiter implements RateLimiter {

    private final ProxyManager<String> proxyManager;

    private final String key;

    private final BucketConfiguration configuration;

    public DistributedRateLimiter(
        final ProxyManager<String> proxyManager, final String key, final int permitsPerMinute) {
        this.proxyManager = proxyManager;
        this.key = key;
        this.configuration = BucketConfiguration.builder()
            .addLimit(Bandwidth.builder()
                .capacity(permitsPerMinute)
                .refillGreedy(permitsPerMinute, Duration.ofMinutes(1))
                .build())
            .build();
    }

    // PMD.DoNotUseThreads: restoring the interrupt flag is the required idiom, not thread management
    @SuppressWarnings("PMD.DoNotUseThreads")
    @Override
    public boolean tryAcquire(final Duration maxWait) {
        try {
            return proxyManager.getProxy(key, () -> configuration)
                .asBlocking().tryConsume(1, maxWait.toNanos());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
