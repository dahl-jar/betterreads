package com.betterreads.features.coverimages;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
class CoverMirrorGate {

    private static final long MAX_TRACKED_ATTEMPTS = 10_000L;

    private final CoverMirrorService coverMirror;

    private final Semaphore permits;

    private final Semaphore waiters;

    private final Cache<String, Boolean> attempted;

    private final Duration maxWait;

    private final ConcurrentMap<String, CompletableFuture<Optional<String>>> running = new ConcurrentHashMap<>();

    @Autowired
    CoverMirrorGate(final CoverMirrorService coverMirror, final CoverMirrorProperties properties) {
        this(coverMirror, properties, Ticker.systemTicker());
    }

    CoverMirrorGate(
        final CoverMirrorService coverMirror, final CoverMirrorProperties properties, final Ticker ticker
    ) {
        this.coverMirror = coverMirror;
        this.maxWait = properties.maxWait();
        this.permits = new Semaphore(properties.maxConcurrent());
        this.waiters = new Semaphore(properties.maxWaiters());
        this.attempted = Caffeine.newBuilder()
            .expireAfterWrite(properties.retryAfter())
            .maximumSize(MAX_TRACKED_ATTEMPTS)
            .ticker(ticker)
            .build();
    }

    Optional<String> mirror(final String dedupKey, final String coverUrl) {
        final String objectKey = CoverMirrorService.objectKey(dedupKey, coverUrl);
        final CompletableFuture<Optional<String>> own = new CompletableFuture<>();
        final CompletableFuture<Optional<String>> leader = running.putIfAbsent(objectKey, own);
        if (leader != null) {
            return await(leader);
        }
        try {
            final Optional<String> stored = mirrorIfAllowed(dedupKey, coverUrl, objectKey);
            own.complete(stored);
            return stored;
        } finally {
            own.complete(Optional.empty());
            running.remove(objectKey, own);
        }
    }

    private Optional<String> await(final CompletableFuture<Optional<String>> leader) {
        if (!waiters.tryAcquire()) {
            return Optional.empty();
        }
        try {
            return leader.copy()
                .completeOnTimeout(Optional.empty(), maxWait.toMillis(), TimeUnit.MILLISECONDS)
                .join();
        } finally {
            waiters.release();
        }
    }

    private Optional<String> mirrorIfAllowed(final String dedupKey, final String coverUrl, final String objectKey) {
        if (attempted.getIfPresent(objectKey) != null || !permits.tryAcquire()) {
            return Optional.empty();
        }
        attempted.put(objectKey, Boolean.TRUE);
        try {
            final Optional<String> stored = coverMirror.mirror(dedupKey, coverUrl);
            stored.ifPresent(attempted::invalidate);
            return stored;
        } finally {
            permits.release();
        }
    }
}
