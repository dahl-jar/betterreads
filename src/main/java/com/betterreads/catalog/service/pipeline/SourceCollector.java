package com.betterreads.catalog.service.pipeline;

import com.betterreads.catalog.service.source.BookFieldSource;
import com.betterreads.catalog.service.source.BookSourceClient;
import com.betterreads.catalog.service.source.MergedBook;
import com.betterreads.catalog.service.source.SourceBook;
import com.betterreads.catalog.service.source.SourceMerger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientException;

/**
 * Fetches the other sources for a staged book and merges them into one.
 *
 * <p>The seed's own discovery source is skipped; the rest run in two waves, the show-field sources
 * first and the low-yield sources (awards, authority cross-references) second, with the sources in a
 * wave fetched concurrently. Both waves always run. A source that fails (5xx, network error,
 * timeout, or an unexpected exception while parsing its response) is dropped for this book and the
 * merge proceeds with the remaining sources.
 */
@Component
public class SourceCollector {

    private static final Logger LOG = LoggerFactory.getLogger(SourceCollector.class);

    private static final Duration PER_CALL_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Fetch waves, ordered by latency. The first wave holds the sources that fill the show fields
     * fast; the second holds the slower, low-yield sources.
     */
    private static final List<List<BookFieldSource>> WAVES = List.of(
        List.of(BookFieldSource.GOOGLE_BOOKS, BookFieldSource.OPEN_LIBRARY),
        List.of(BookFieldSource.HARDCOVER, BookFieldSource.LOC, BookFieldSource.WIKIDATA));

    private static final List<BookFieldSource> FETCH_ORDER = WAVES.stream().flatMap(List::stream).toList();

    private final SourceMerger merger;

    private final List<BookSourceClient> sourceClients;

    private final Executor executor;

    public SourceCollector(
        final SourceMerger merger,
        final List<BookSourceClient> sourceClients,
        @Qualifier("sourceFetchExecutor") final Executor sourceFetchExecutor
    ) {
        this.merger = merger;
        this.sourceClients = order(sourceClients);
        this.executor = sourceFetchExecutor;
    }

    /**
     * Fetches every source for the seed, wave by wave, and returns the merge with the matches.
     *
     * <p>Both waves always run, so the merge sees every source, including the enrichment fields the
     * show bar does not require (rating, awards, full genre, page count, author identity). The waves
     * order the fetches by latency, not by a stop condition: the fast show-field sources first, the
     * slow ones after, each wave fetched concurrently.
     */
    public MergedBook collectFor(final SourceBook seed) {
        final List<SourceBook> found = new ArrayList<>();
        found.add(seed);
        for (final List<BookFieldSource> wave : WAVES) {
            found.addAll(fetchWave(wave, seed));
        }
        return merger.merge(seed, found);
    }

    private List<SourceBook> fetchWave(final List<BookFieldSource> wave, final SourceBook seed) {
        final List<CompletableFuture<Optional<SourceBook>>> calls = sourceClients.stream()
            .filter(client -> client.source() != null && wave.contains(client.source()))
            .filter(client -> client.source() != seed.source())
            .map(client -> CompletableFuture.supplyAsync(() -> fetch(client, seed), executor))
            .toList();
        awaitWave(calls);
        return calls.stream()
            .flatMap(SourceCollector::completedValue)
            .toList();
    }

    /**
     * Waits up to one timeout for the whole wave, then cancels whatever is still running. One timeout
     * covers the wave, not one per source. A failed source is harvested as empty by
     * {@link #completedValue}; the rest still merge.
     */
    @SuppressWarnings("PMD.DoNotUseThreads")
    private static void awaitWave(final List<CompletableFuture<Optional<SourceBook>>> calls) {
        try {
            CompletableFuture.allOf(calls.toArray(CompletableFuture[]::new))
                .get(PER_CALL_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            calls.forEach(call -> call.cancel(true));
        } catch (ExecutionException ex) {
            final Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            LOG.warn("catalog.collect a source fetch threw {}", cause.getClass().getSimpleName(), cause);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static Stream<SourceBook> completedValue(final CompletableFuture<Optional<SourceBook>> call) {
        if (!call.isDone() || call.isCompletedExceptionally() || call.isCancelled()) {
            return Stream.empty();
        }
        return call.getNow(Optional.empty()).stream();
    }

    private static List<BookSourceClient> order(final List<BookSourceClient> clients) {
        return clients.stream()
            .sorted(Comparator.comparingInt(client -> rank(client.source())))
            .toList();
    }

    private static int rank(final @Nullable BookFieldSource source) {
        if (source == null) {
            return FETCH_ORDER.size();
        }
        final int index = FETCH_ORDER.indexOf(source);
        return index < 0 ? FETCH_ORDER.size() : index;
    }

    private static Optional<SourceBook> fetch(final BookSourceClient client, final SourceBook seed) {
        try {
            final String isbn = seed.isbn13();
            if (isbn != null) {
                final Optional<SourceBook> byIsbn = client.fetchByIsbn(isbn);
                if (byIsbn.isPresent()) {
                    return byIsbn;
                }
            }
            return fetchByTitleAuthor(client, seed);
        } catch (WebClientException ex) {
            LOG.warn("catalog.collect source {} failed ({}), skipping it for this book",
                client.source(), ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private static Optional<SourceBook> fetchByTitleAuthor(
        final BookSourceClient client, final SourceBook seed) {
        final String title = seed.title();
        final List<String> authors = seed.authorNames();
        if (title == null || authors == null || authors.isEmpty()) {
            return Optional.empty();
        }
        return client.fetchByTitleAuthor(title, firstAuthor(authors));
    }

    private static String firstAuthor(final List<String> authors) {
        return authors.get(0);
    }
}
