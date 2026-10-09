package com.betterreads.features.coverimages;

import static com.betterreads.features.coverimages.CoverImageFixtures.APPLE_COVER;
import static com.betterreads.features.coverimages.CoverImageFixtures.ISBN;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

// PMD.DoNotUseThreads: concurrent requests need real threads
@SuppressWarnings("PMD.DoNotUseThreads")
class CoverMirrorWaitTest extends CoverMirrorGateFixture {

    private static final Duration SHORT_WAIT = Duration.ofMillis(200);

    private static final Duration PROMPT = Duration.ofSeconds(2);

    @Test
    void shouldAnswerWaitersWhenTheMirrorThrows() throws ExecutionException, InterruptedException,
        TimeoutException {
        final CoverMirrorGate gate = gate(LIMIT, MAX_WAIT, WAITERS);
        holdThenThrow(APPLE_COVER);
        final List<Thread> threads = new CopyOnWriteArrayList<>();
        final Optional<String> answer;
        try (ExecutorService pool = Executors.newFixedThreadPool(2, track(threads))) {
            assertThat(lead(pool, gate)).isTrue();
            final Future<Optional<String>> waiter = pool.submit(() -> gate.mirror(ISBN, APPLE_COVER));
            awaitParked(threads, 2);
            release().countDown();

            answer = waiter.get(WAIT_SECONDS, TimeUnit.SECONDS);
        }

        assertThat(answer).isEmpty();
    }

    @Test
    void shouldStopOneWaiterWithoutCuttingOffTheOthers() throws ExecutionException, InterruptedException,
        TimeoutException {
        final CoverMirrorGate gate = gate(LIMIT, SHORT_WAIT, 1);
        hold(APPLE_COVER, OBJECT_KEY);
        final List<Thread> threads = new CopyOnWriteArrayList<>();
        final Optional<String> gaveUp;
        final Duration waited;
        final Optional<String> later;
        try (ExecutorService pool = Executors.newFixedThreadPool(2, track(threads))) {
            assertThat(lead(pool, gate)).isTrue();
            final long before = System.nanoTime();

            gaveUp = gate.mirror(ISBN, APPLE_COVER);

            waited = Duration.ofNanos(System.nanoTime() - before);
            final Future<Optional<String>> waiter = pool.submit(() -> gate.mirror(ISBN, APPLE_COVER));
            awaitParked(threads, 2);
            release().countDown();
            later = waiter.get(WAIT_SECONDS, TimeUnit.SECONDS);
        }

        assertThat(gaveUp).isEmpty();
        assertThat(waited).isLessThan(PROMPT);
        assertThat(later).contains(OBJECT_KEY);
    }

    @Test
    void shouldTurnAwayWaitersOverTheLimit() throws InterruptedException {
        final CoverMirrorGate gate = gate(LIMIT, MAX_WAIT, 1);
        hold(APPLE_COVER, OBJECT_KEY);
        final List<Thread> threads = new CopyOnWriteArrayList<>();
        final Optional<String> turnedAway;
        final Duration waited;
        try (ExecutorService pool = Executors.newFixedThreadPool(2, track(threads))) {
            assertThat(lead(pool, gate)).isTrue();
            pool.execute(() -> gate.mirror(ISBN, APPLE_COVER));
            awaitParked(threads, 2);
            final long before = System.nanoTime();

            turnedAway = gate.mirror(ISBN, APPLE_COVER);

            waited = Duration.ofNanos(System.nanoTime() - before);
            release().countDown();
        }

        assertThat(turnedAway).isEmpty();
        assertThat(waited).isLessThan(PROMPT);
    }
}
