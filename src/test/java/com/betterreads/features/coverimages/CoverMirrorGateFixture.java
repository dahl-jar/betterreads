package com.betterreads.features.coverimages;

import static com.betterreads.features.coverimages.CoverImageFixtures.APPLE_COVER;
import static com.betterreads.features.coverimages.CoverImageFixtures.HARDCOVER_COVER;
import static com.betterreads.features.coverimages.CoverImageFixtures.ISBN;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

// PMD.DoNotUseThreads: concurrent requests need real threads.
// PMD.AbstractClassWithoutAbstractMethod: a shared-fixture base with no abstract steps.
@SuppressWarnings({"PMD.DoNotUseThreads", "PMD.AbstractClassWithoutAbstractMethod"})
abstract class CoverMirrorGateFixture {

    static final String OBJECT_KEY = CoverMirrorService.objectKey(ISBN, APPLE_COVER);

    static final String OTHER_KEY = CoverMirrorService.objectKey(ISBN, HARDCOVER_COVER);

    static final String UNREADABLE = "unreadable image";

    static final int LIMIT = 4;

    static final int WAITERS = 8;

    static final long WAIT_SECONDS = 10;

    static final Duration RETRY_AFTER = Duration.ofMinutes(2);

    static final Duration MAX_WAIT = Duration.ofMinutes(1);

    private static final Set<Thread.State> PARKED = EnumSet.of(Thread.State.WAITING, Thread.State.TIMED_WAITING);

    private final CoverMirrorService mirrorService = mock(CoverMirrorService.class);

    private final AtomicLong clock = new AtomicLong();

    private final Semaphore startedCalls = new Semaphore(0);

    private final CountDownLatch releaseLatch = new CountDownLatch(1);

    CoverMirrorGate gate(final int limit, final Duration maxWait, final int waiters) {
        return new CoverMirrorGate(
            mirrorService, new CoverMirrorProperties(limit, RETRY_AFTER, maxWait, waiters), clock::get);
    }

    void hold(final String coverUrl, final String objectKey) {
        when(mirrorService.mirror(ISBN, coverUrl))
            .thenAnswer(call -> startThenWait() ? Optional.of(objectKey) : Optional.empty());
    }

    void holdThenThrow(final String coverUrl) {
        when(mirrorService.mirror(ISBN, coverUrl)).thenAnswer(call -> {
            if (startThenWait()) {
                throw new IllegalStateException(UNREADABLE);
            }
            return Optional.empty();
        });
    }

    boolean lead(final ExecutorService pool, final CoverMirrorGate gate) throws InterruptedException {
        pool.execute(() -> gate.mirror(ISBN, APPLE_COVER));
        return awaitStarted(1);
    }

    boolean awaitStarted(final int calls) throws InterruptedException {
        return startedCalls.tryAcquire(calls, WAIT_SECONDS, TimeUnit.SECONDS);
    }

    static void awaitParked(final List<Thread> threads, final int count) {
        await().until(() -> threads.size() == count
            && threads.stream().allMatch(thread -> PARKED.contains(thread.getState())));
    }

    static ThreadFactory track(final List<Thread> threads) {
        return task -> {
            final Thread thread = new Thread(task);
            threads.add(thread);
            return thread;
        };
    }

    private boolean startThenWait() throws InterruptedException {
        startedCalls.release();
        return releaseLatch.await(WAIT_SECONDS, TimeUnit.SECONDS);
    }

    CoverMirrorService coverMirror() {
        return mirrorService;
    }

    AtomicLong nanos() {
        return clock;
    }

    CountDownLatch release() {
        return releaseLatch;
    }
}
