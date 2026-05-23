package com.betterreads.search.service;

import jakarta.annotation.PostConstruct;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Pushes the canonical book set from Postgres into the Meilisearch index.
 *
 * <p>Postgres is the source of truth; the index is a derived view. The
 * reconciler is safe to re-run at any cadence because documents are upserted
 * by id.
 *
 * <p>TODO(implementer): {@link #reconcile()} body is not implemented.
 */
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
