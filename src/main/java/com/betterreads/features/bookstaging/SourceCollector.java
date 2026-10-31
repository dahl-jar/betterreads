package com.betterreads.features.bookstaging;

import com.betterreads.bookdescription.DescriptionSelector;
import com.betterreads.bookmerge.SourceMerger;
import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.BookSourceClient;
import com.betterreads.booksource.MergedBook;
import com.betterreads.booksource.SourceBook;
import com.betterreads.logging.LogSanitizer;
import com.betterreads.text.TextMatch;

import java.time.Duration;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientException;

@Component
public class SourceCollector {

    private static final Logger LOG = LoggerFactory.getLogger(SourceCollector.class);

    private static final Duration WAVE_TIMEOUT = Duration.ofSeconds(30);

    private static final List<List<BookFieldSource>> WAVES = List.of(
        List.of(BookFieldSource.GOOGLE_BOOKS, BookFieldSource.OPEN_LIBRARY),
        List.of(BookFieldSource.HARDCOVER, BookFieldSource.LOC, BookFieldSource.WIKIDATA));

    private static final List<BookFieldSource> FETCH_ORDER = WAVES.stream().flatMap(List::stream).toList();

    private final SourceMerger merger;

    private final List<BookSourceClient> sourceClients;

    private final DescriptionSelector descriptionSelector;

    private final Executor executor;

    private final Duration waveTimeout;

    @Autowired
    public SourceCollector(
        final SourceMerger merger,
        final List<BookSourceClient> sourceClients,
        final DescriptionSelector descriptionSelector,
        @Qualifier("sourceFetchExecutor") final Executor sourceFetchExecutor
    ) {
        this(merger, sourceClients, descriptionSelector, sourceFetchExecutor, WAVE_TIMEOUT);
    }

    SourceCollector(
        final SourceMerger merger,
        final List<BookSourceClient> sourceClients,
        final DescriptionSelector descriptionSelector,
        final Executor sourceFetchExecutor,
        final Duration waveTimeout
    ) {
        this.merger = merger;
        this.sourceClients = order(sourceClients);
        this.descriptionSelector = descriptionSelector;
        this.executor = sourceFetchExecutor;
        this.waveTimeout = waveTimeout;
    }

    public MergedBook collectFor(final SourceBook seed) {
        final List<Fetched> fetched = WAVES.stream()
            .flatMap(wave -> fetchWave(wave, seed).stream())
            .toList();
        final List<SourceBook> found = Stream.concat(
                Stream.of(seed),
                fetched.stream().map(Fetched::book))
            .toList();
        final Set<BookFieldSource> resolved = EnumSet.of(seed.source());
        fetched.forEach(result -> resolved.add(result.source()));
        return descriptionSelector.withBestDescription(merger.merge(seed, found).withResolvedSources(resolved));
    }

    private List<Fetched> fetchWave(final List<BookFieldSource> wave, final SourceBook seed) {
        final List<CompletableFuture<Fetched>> calls = sourceClients.stream()
            .filter(client -> wave.contains(client.source()))
            .filter(client -> client.source() != seed.source())
            .map(client -> CompletableFuture.supplyAsync(() -> fetch(client, seed), executor))
            .toList();
        awaitWave(calls);
        return calls.stream()
            .flatMap(SourceCollector::completed)
            .toList();
    }

    // PMD.DoNotUseThreads: the catch has to restore the interrupt flag on the current thread
    @SuppressWarnings("PMD.DoNotUseThreads")
    private void awaitWave(final List<CompletableFuture<Fetched>> calls) {
        try {
            CompletableFuture.allOf(calls.toArray(CompletableFuture[]::new))
                .get(waveTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            calls.forEach(call -> call.cancel(true));
        } catch (ExecutionException ex) {
            final Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            LOG.warn("catalog.collect a source fetch threw {}", cause.getClass().getSimpleName(), cause);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static Stream<Fetched> completed(final CompletableFuture<Fetched> call) {
        if (!call.isDone() || call.isCompletedExceptionally()) {
            return Stream.empty();
        }
        return Stream.ofNullable(call.getNow(null));
    }

    private static List<BookSourceClient> order(final List<BookSourceClient> clients) {
        return clients.stream()
            .sorted(Comparator.comparingInt(client -> rank(client.source())))
            .toList();
    }

    private static int rank(final BookFieldSource source) {
        final int index = FETCH_ORDER.indexOf(source);
        return index < 0 ? FETCH_ORDER.size() : index;
    }

    private static @Nullable Fetched fetch(final BookSourceClient client, final SourceBook seed) {
        try {
            return match(client, seed).map(book -> new Fetched(client.source(), book)).orElse(null);
        } catch (WebClientException ex) {
            LOG.warn("catalog.collect source {} failed ({}), skipping it for this book",
                client.source(), ex.getClass().getSimpleName());
            return null;
        }
    }

    private static Optional<SourceBook> match(final BookSourceClient client, final SourceBook seed) {
        return Optional.ofNullable(seed.isbn13())
            .flatMap(client::fetchByIsbn)
            .filter(hit -> keepIsbnHit(client.source(), seed, hit))
            .or(() -> fetchByTitleAuthor(client, seed));
    }

    private static boolean keepIsbnHit(
        final BookFieldSource source, final SourceBook seed, final SourceBook hit) {
        final String seedTitle = seed.title();
        final String hitTitle = hit.title();
        if (seedTitle == null || hitTitle == null || TextMatch.sameBookTitle(seedTitle, hitTitle)) {
            return true;
        }
        LOG.warn(
            "catalog.collect dropped ISBN hit with another title source={} isbn={} seedTitle=\"{}\" hitTitle=\"{}\"",
            source, LogSanitizer.forLog(seed.isbn13()), LogSanitizer.forLog(seedTitle), LogSanitizer.forLog(hitTitle));
        return false;
    }

    private static Optional<SourceBook> fetchByTitleAuthor(
        final BookSourceClient client, final SourceBook seed) {
        final String title = seed.title();
        final List<String> authors = seed.authorNames();
        if (title == null || authors == null || authors.isEmpty()) {
            return Optional.empty();
        }
        return client.fetchByTitleAuthor(title, authors.get(0));
    }

    private record Fetched(BookFieldSource source, SourceBook book) {
    }
}
