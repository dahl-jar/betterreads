package com.betterreads.catalog.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientException;

/**
 * Fetches the sources a staged book still needs and merges them into one.
 *
 * <p>The seed already carries its discovery source's fields, so that source is not fetched again.
 * The rest are tried in priority order and the fan-out stops as soon as the book has the fields
 * needed to show it, so a well-covered book costs one or two calls instead of one per source. A
 * source that fails (5xx or network error) is dropped for this book and the merge proceeds with the
 * rest, so one flaky source does not sink a candidate or the surrounding batch.
 */
@Component
public class SourceCollector {

    private static final Logger LOG = LoggerFactory.getLogger(SourceCollector.class);

    /**
     * Fetch order. Sources that fill the show fields in one call come first; the low-yield sources
     * (awards, authority cross-references) come last, so the gate-stop skips them for books that are
     * already showable.
     */
    private static final List<BookFieldSource> FETCH_ORDER = List.of(
        BookFieldSource.GOOGLE_BOOKS,
        BookFieldSource.OPEN_LIBRARY,
        BookFieldSource.HARDCOVER,
        BookFieldSource.LOC,
        BookFieldSource.WIKIDATA);

    private final SourceMerger merger;

    private final RequiredFieldsCheck requiredFields;

    private final List<BookSourceClient> sourceClients;

    public SourceCollector(
        final SourceMerger merger,
        final RequiredFieldsCheck requiredFields,
        final List<BookSourceClient> sourceClients
    ) {
        this.merger = merger;
        this.requiredFields = requiredFields;
        this.sourceClients = order(sourceClients);
    }

    /** Fetches the needed sources for the seed and returns the merge of the seed and the matches. */
    public MergedBook collectFor(final SourceBook seed) {
        final List<SourceBook> found = new ArrayList<>();
        found.add(seed);
        MergedBook merged = merger.merge(found);
        for (final BookSourceClient client : sourceClients) {
            if (requiredFields.check(merged.book()).isReady()) {
                break;
            }
            if (client.source() == seed.source()) {
                continue;
            }
            final Optional<SourceBook> hit = fetch(client, seed);
            if (hit.isPresent()) {
                found.add(hit.get());
                merged = merger.merge(found);
            }
        }
        return merged;
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
                return client.fetchByIsbn(isbn);
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
