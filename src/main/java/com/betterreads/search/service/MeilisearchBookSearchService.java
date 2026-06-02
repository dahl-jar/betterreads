package com.betterreads.search.service;

import com.betterreads.search.config.MeilisearchProperties;
import com.betterreads.search.dto.BookSearchDocument;
import com.betterreads.search.dto.BookSearchResult;
import com.meilisearch.sdk.Client;
import jakarta.annotation.PostConstruct;
import java.util.Collection;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Meilisearch-backed implementation of {@link BookSearchService}.
 */
// TODO(2026-Q3): implement search/index/remove against the Meilisearch SDK; methods currently throw
@Service
@RequiredArgsConstructor
public class MeilisearchBookSearchService implements BookSearchService {

    private static final String NOT_IMPLEMENTED = "not yet implemented";

    private final Client client;
    private final MeilisearchProperties props;

    /**
     * Fail-fast assertion that DI wired the client and properties.
     */
    @PostConstruct
    void assertWired() {
        Objects.requireNonNull(client, "Meilisearch client must be wired");
        Objects.requireNonNull(props, "MeilisearchProperties must be wired");
    }

    @Override
    public BookSearchResult search(final String query, final int offset, final int limit) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public void index(final Collection<BookSearchDocument> documents) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public void remove(final String bookId) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }
}
