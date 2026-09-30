package com.betterreads.features.search;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;

import com.betterreads.bookindex.BookIndexViewReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Re-indexes books changed in the last two days, so a book missed during a short search outage becomes searchable. */
@Component
final class BookIndexReconciler {

    private static final Logger LOG = LoggerFactory.getLogger(BookIndexReconciler.class);

    private static final Duration LOOKBACK = Duration.ofDays(2);

    private final BookIndexViewReader indexViews;

    private final BookSearchDocumentMapper mapper;

    private final BookSearchService searchService;

    private final int pageSize;

    BookIndexReconciler(
        final BookIndexViewReader indexViews,
        final BookSearchDocumentMapper mapper,
        final BookSearchService searchService,
        @Value("${betterreads.search.reconcile-page-size:500}") final int pageSize
    ) {
        if (pageSize < 1) {
            throw new IllegalArgumentException("betterreads.search.reconcile-page-size must be at least 1");
        }
        this.indexViews = indexViews;
        this.mapper = mapper;
        this.searchService = searchService;
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
    }

    private int index(final List<Long> ids) {
        searchService.index(indexViews.indexViewsByIds(ids).stream().map(mapper::toDocument).toList());
        return ids.size();
    }
}
