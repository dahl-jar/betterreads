package com.betterreads.features.search;

import java.util.List;

import com.betterreads.bookindex.BookIndexViewReader;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Re-indexes the whole catalog every night, so a book the promotion listener missed during a
 * Meilisearch outage still becomes searchable.
 */
@Component
@RequiredArgsConstructor
class BookIndexReconciler {

    private static final Logger LOG = LoggerFactory.getLogger(BookIndexReconciler.class);

    private final BookIndexViewReader indexViews;

    private final BookSearchDocumentMapper mapper;

    private final BookSearchService searchService;

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional(readOnly = true)
    public void reconcile() {
        final List<BookSearchDocument> documents = indexViews.allForIndex().stream()
            .map(mapper::toDocument)
            .toList();
        searchService.index(documents);
        LOG.info("search.reconcile indexed {} books", documents.size());
    }
}
