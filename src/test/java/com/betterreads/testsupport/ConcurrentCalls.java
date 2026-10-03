package com.betterreads.testsupport;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;

public final class ConcurrentCalls {

    private static final long TIMEOUT_SECONDS = 10;

    private ConcurrentCalls() {
    }

    // PMD.DoNotUseThreads: the race needs real threads
    @SuppressWarnings("PMD.DoNotUseThreads")
    public static <T> List<T> run(final int threads, final Callable<T> action)
        throws InterruptedException, ExecutionException, TimeoutException {
        final CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            final List<Future<T>> calls = IntStream.range(0, threads)
                .mapToObj(i -> pool.submit(() -> {
                    start.await();
                    return action.call();
                }))
                .toList();
            start.countDown();
            final List<T> results = new ArrayList<>(threads);
            for (final Future<T> call : calls) {
                results.add(call.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
            return results;
        }
    }
}
