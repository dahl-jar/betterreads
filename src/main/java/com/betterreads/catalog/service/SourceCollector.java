package com.betterreads.catalog.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * Fetches every source for a staged book and merges them into one.
 *
 * <p>A candidate staged from a single search hit carries only that source's fields. The collector
 * fetches the rest by the candidate's ISBN, or by title and author when there is no ISBN, and
 * merges the seed with what it finds, so the candidate gains the rating, description, and other
 * fields it needs to pass the show bar.
 */
@Component
public class SourceCollector {

    private final SourceMerger merger;

    private final List<BookSourceClient> sourceClients;

    public SourceCollector(final SourceMerger merger, final List<BookSourceClient> sourceClients) {
        this.merger = merger;
        this.sourceClients = List.copyOf(sourceClients);
    }

    /** Fetches every source for the seed book and returns the merge of the seed and the matches. */
    public MergedBook collectFor(final SourceBook seed) {
        final List<SourceBook> found = new ArrayList<>();
        found.add(seed);
        for (final BookSourceClient client : sourceClients) {
            fetch(client, seed).ifPresent(found::add);
        }
        return merger.merge(found);
    }

    private static Optional<SourceBook> fetch(final BookSourceClient client, final SourceBook seed) {
        final String isbn = seed.isbn13();
        if (isbn != null) {
            return client.fetchByIsbn(isbn);
        }
        return fetchByTitleAuthor(client, seed);
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
