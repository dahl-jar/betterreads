package com.betterreads.search.service;

import java.util.List;

import com.betterreads.catalog.repository.BookRepository;
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
 * <p>Safe to re-run at any cadence because documents are upserted by id. This is the floor that
 * heals any book the per-promotion index hook missed, for example after a Meilisearch outage.
 */
@Component
@RequiredArgsConstructor
public class BookIndexReconciler {

    private static final Logger LOG = LoggerFactory.getLogger(BookIndexReconciler.class);

    private final BookRepository books;

    private final BookSearchDocumentMapper mapper;

    private final BookSearchService searchService;

    /** Indexes every catalog book. */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional(readOnly = true)
    public void reconcile() {
        final List<BookSearchDocument> documents = books.findAllBy().stream()
            .map(mapper::toDocument)
            .toList();
        searchService.index(documents);
        LOG.info("search.reconcile indexed {} books", documents.size());
    }
}
