package com.betterreads.search.service;

import jakarta.annotation.PostConstruct;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Pushes the canonical book set from Postgres into the Meilisearch index.
 *
 * <p>Safe to re-run at any cadence because documents are upserted by id.
 */
// TODO(2026-Q3): implement reconcile() against the Meilisearch SDK; currently throws
@Component
@RequiredArgsConstructor
public class BookIndexReconciler {

    private final BookSearchService searchService;

    /**
     * Fail-fast assertion that DI wired the search service.
     */
    @PostConstruct
    void assertWired() {
        Objects.requireNonNull(searchService, "BookSearchService must be wired");
    }

    /**
     * Reconciles the entire catalog into the search index.
     */
    @Scheduled(cron = "0 30 3 * * *")
    public void reconcile() {
        throw new UnsupportedOperationException("not yet implemented");
    }
}
