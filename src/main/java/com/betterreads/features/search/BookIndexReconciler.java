package com.betterreads.features.search;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Gatherers;
import java.util.stream.Stream;

import com.betterreads.bookindex.BookIndexViewReader;
import com.betterreads.logging.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Re-indexes books changed in the last two days, then adds every book missing from the index
 * and drops documents with no book.
 */
@Component
final class BookIndexReconciler {

    private static final Logger LOG = LoggerFactory.getLogger(BookIndexReconciler.class);

    private static final Duration LOOKBACK = Duration.ofDays(2);

    private final BookIndexViewReader indexViews;

    private final BookSearchDocumentMapper mapper;

    private final BookSearchService searchService;

    private final BookSearchRepository books;

    private final int pageSize;

    BookIndexReconciler(
        final BookIndexViewReader indexViews,
        final BookSearchDocumentMapper mapper,
        final BookSearchService searchService,
        final BookSearchRepository books,
        @Value("${betterreads.search.reconcile-page-size:500}") final int pageSize
    ) {
        if (pageSize < 1) {
            throw new IllegalArgumentException("betterreads.search.reconcile-page-size must be at least 1");
        }
        this.indexViews = indexViews;
        this.mapper = mapper;
        this.searchService = searchService;
        this.books = books;
        this.pageSize = pageSize;
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void reconcile() {
        final OffsetDateTime since = OffsetDateTime.now(ZoneOffset.UTC).minus(LOOKBACK);
        final int indexed = Stream.iterate(
                indexViews.idsChangedSince(since, 0, pageSize),
                ids -> !ids.isEmpty(),
                ids -> indexViews.idsChangedSince(since, ids.getLast(), pageSize))
            .mapToInt(this::index)
            .sum();
        LOG.info("search.reconcile indexed {} books", indexed);
        try {
            reconcileAll();
        } catch (SearchIndexException ex) {
            LOG.warn("search.reconcile-full-failed ({})", LogSanitizer.forLog(ex.getMessage()));
        }
    }

    private void reconcileAll() {
        final Set<String> documents = searchService.indexedIds(pageSize);
        final Set<String> keys = dedupKeys();
        final List<String> missing = keys.stream().filter(key -> !documents.contains(key)).toList();
        final List<String> orphaned = documents.stream().filter(id -> !keys.contains(id)).toList();
        missing.stream()
            .gather(Gatherers.windowFixed(pageSize))
            .forEach(page -> index(books.findIdsByDedupKeyIn(page)));
        searchService.deleteAll(orphaned);
        LOG.info("search.reconcile-full missing={} orphaned={}", missing.size(), orphaned.size());
    }

    private Set<String> dedupKeys() {
        return Stream.iterate(
                books.findDedupKeysAfter("", PageRequest.ofSize(pageSize)),
                page -> !page.isEmpty(),
                page -> books.findDedupKeysAfter(page.getLast(), PageRequest.ofSize(pageSize)))
            .flatMap(List::stream)
            .collect(Collectors.toSet());
    }

    private int index(final List<Long> ids) {
        searchService.index(indexViews.indexViewsByIds(ids).stream().map(mapper::toDocument).toList());
        return ids.size();
    }
}
