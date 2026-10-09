package com.betterreads.features.coverimages;

import static com.betterreads.features.coverimages.CoverImageFixtures.APPLE_COVER;
import static com.betterreads.features.coverimages.CoverImageFixtures.HARDCOVER_COVER;
import static com.betterreads.features.coverimages.CoverImageFixtures.ISBN;
import static com.betterreads.features.coverimages.CoverImageFixtures.OPEN_LIBRARY_COVER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

// PMD.DoNotUseThreads: concurrent requests need real threads
@SuppressWarnings("PMD.DoNotUseThreads")
class CoverMirrorGateTest extends CoverMirrorGateFixture {

    private static final String OPEN_LIBRARY_KEY = CoverMirrorService.objectKey(ISBN, OPEN_LIBRARY_COVER);

    private static final int REQUESTS = 5;

    @Test
    void shouldMirrorACoverOnceForConcurrentRequests() {
        final CoverMirrorGate gate = gate(LIMIT, MAX_WAIT, WAITERS);
        hold(APPLE_COVER, OBJECT_KEY);
        final List<Thread> threads = new CopyOnWriteArrayList<>();
        final List<Future<Optional<String>>> calls;
        try (ExecutorService pool = Executors.newFixedThreadPool(REQUESTS, track(threads))) {
            calls = IntStream.range(0, REQUESTS)
                .mapToObj(i -> pool.submit(() -> gate.mirror(ISBN, APPLE_COVER)))
                .toList();
            awaitParked(threads, REQUESTS);

            release().countDown();
        }

        assertThat(calls).extracting(Future::resultNow).containsOnly(Optional.of(OBJECT_KEY));
        verify(coverMirror(), times(1)).mirror(ISBN, APPLE_COVER);
    }

    @Test
    void shouldRefuseAMirrorOverTheLimitForThatRequestOnly() throws InterruptedException {
        final CoverMirrorGate gate = gate(2, MAX_WAIT, WAITERS);
        hold(APPLE_COVER, OBJECT_KEY);
        hold(HARDCOVER_COVER, OTHER_KEY);
        final Optional<String> refused;
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            pool.execute(() -> gate.mirror(ISBN, APPLE_COVER));
            pool.execute(() -> gate.mirror(ISBN, HARDCOVER_COVER));
            assertThat(awaitStarted(2)).isTrue();

            refused = gate.mirror(ISBN, OPEN_LIBRARY_COVER);

            release().countDown();
        }
        when(coverMirror().mirror(ISBN, OPEN_LIBRARY_COVER)).thenReturn(Optional.of(OPEN_LIBRARY_KEY));
        final Optional<String> retried = gate.mirror(ISBN, OPEN_LIBRARY_COVER);

        assertThat(refused).isEmpty();
        assertThat(retried).contains(OPEN_LIBRARY_KEY);
        verify(coverMirror(), times(1)).mirror(ISBN, OPEN_LIBRARY_COVER);
    }

    @ParameterizedTest
    @CsvSource({"1, 1", "3, 2"})
    void shouldRetryAFailedCoverOnlyAfterTheWindow(final long minutesLater, final int mirrors) {
        final CoverMirrorGate gate = gate(LIMIT, MAX_WAIT, WAITERS);
        when(coverMirror().mirror(ISBN, APPLE_COVER)).thenReturn(Optional.empty());
        gate.mirror(ISBN, APPLE_COVER);
        nanos().addAndGet(Duration.ofMinutes(minutesLater).toNanos());

        gate.mirror(ISBN, APPLE_COVER);

        verify(coverMirror(), times(mirrors)).mirror(ISBN, APPLE_COVER);
    }

    @Test
    void shouldMirrorACoverAgainAfterASuccess() {
        final CoverMirrorGate gate = gate(LIMIT, MAX_WAIT, WAITERS);
        when(coverMirror().mirror(ISBN, APPLE_COVER)).thenReturn(Optional.of(OBJECT_KEY));
        gate.mirror(ISBN, APPLE_COVER);

        final Optional<String> again = gate.mirror(ISBN, APPLE_COVER);

        assertThat(again).contains(OBJECT_KEY);
        verify(coverMirror(), times(2)).mirror(ISBN, APPLE_COVER);
    }

    @ParameterizedTest
    @CsvSource({"1, 1", "3, 2"})
    void shouldRetryACoverWhoseMirrorThrewOnlyAfterTheWindow(final long minutesLater, final int mirrors) {
        final CoverMirrorGate gate = gate(LIMIT, MAX_WAIT, WAITERS);
        when(coverMirror().mirror(ISBN, APPLE_COVER))
            .thenThrow(new IllegalStateException(UNREADABLE))
            .thenReturn(Optional.empty());
        assertThatThrownBy(() -> gate.mirror(ISBN, APPLE_COVER)).isInstanceOf(IllegalStateException.class);
        nanos().addAndGet(Duration.ofMinutes(minutesLater).toNanos());

        gate.mirror(ISBN, APPLE_COVER);

        verify(coverMirror(), times(mirrors)).mirror(ISBN, APPLE_COVER);
    }

    @Test
    void shouldMirrorOtherCoversAfterAMirrorThrows() {
        final CoverMirrorGate gate = gate(1, MAX_WAIT, WAITERS);
        when(coverMirror().mirror(ISBN, APPLE_COVER)).thenThrow(new IllegalStateException(UNREADABLE));
        when(coverMirror().mirror(ISBN, HARDCOVER_COVER)).thenReturn(Optional.of(OTHER_KEY));
        assertThatThrownBy(() -> gate.mirror(ISBN, APPLE_COVER)).isInstanceOf(IllegalStateException.class);

        final Optional<String> next = gate.mirror(ISBN, HARDCOVER_COVER);

        assertThat(next).contains(OTHER_KEY);
    }
}
