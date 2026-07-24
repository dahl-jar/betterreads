package com.betterreads.search.service;

import java.util.List;

import com.betterreads.catalog.service.read.BookIndexViewReader;
import com.betterreads.search.dto.BookSearchDocument;
import com.betterreads.search.mapper.BookSearchDocumentMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pushes the full catalog into the Meilisearch index nightly.
 *
 * <p>Documents are upserted by id, so a re-run at any cadence is safe. Picks up any book the
 * per-promotion listener missed, for example after a Meilisearch outage.
 */
@Component
@RequiredArgsConstructor
public class BookIndexReconciler {

    private static final Logger LOG = LoggerFactory.getLogger(BookIndexReconciler.class);

    private final BookIndexViewReader indexViews;

    private final BookSearchDocumentMapper mapper;

    private final BookSearchService searchService;

    /** Indexes every catalog book. */
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
