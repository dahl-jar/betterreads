package com.betterreads.ratelimit;

import java.time.Duration;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;

/** Rate limiter backed by a Bucket4j bucket in Redis, so the limit holds across every replica. */
public final class DistributedRateLimiter implements RateLimiter {

    private final ProxyManager<String> proxyManager;

    private final String key;

    private final BucketConfiguration configuration;

    public DistributedRateLimiter(
        final ProxyManager<String> proxyManager, final String key, final int permitsPerMinute) {
        this.proxyManager = proxyManager;
        this.key = key;
        this.configuration = greedyBucket(permitsPerMinute, permitsPerMinute, Duration.ofMinutes(1));
    }

    static BucketConfiguration greedyBucket(
        final long capacity, final long refillTokens, final Duration period) {
        final Bandwidth bandwidth = Bandwidth.builder()
            .capacity(capacity)
            .refillGreedy(refillTokens, period)
            .build();
        return BucketConfiguration.builder().addLimit(bandwidth).build();
    }

    // PMD.DoNotUseThreads: restores the interrupt flag after InterruptedException.
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
