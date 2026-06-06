package com.betterreads.search.service;

import com.betterreads.common.util.LogSanitizer;
import com.betterreads.search.config.MeilisearchProperties;
import com.betterreads.search.dto.BookSearchDocument;
import com.betterreads.search.dto.BookSearchResult;
import com.betterreads.search.dto.SearchOutcome;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.SearchRequest;
import com.meilisearch.sdk.exceptions.MeilisearchException;
import com.meilisearch.sdk.model.SearchResult;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Meilisearch-backed {@link BookSearchService}.
 *
 * <p>A search failure returns an empty result so a Meilisearch outage degrades search rather than
 * failing the request; index and remove failures propagate so a background write is never lost
 * silently.
 */
@Service
@RequiredArgsConstructor
public class MeilisearchBookSearchService implements BookSearchService {

    private static final Logger LOG = LoggerFactory.getLogger(MeilisearchBookSearchService.class);

    private final Client client;

    private final MeilisearchProperties props;

    private final ObjectMapper objectMapper;

    @Override
    public BookSearchResult search(final String query, final int offset, final int limit) {
        return searchOutcome(query, offset, limit).result();
    }

    @Override
    @Cacheable(cacheNames = "searchResults", cacheManager = "searchCacheManager",
        unless = "#result.degraded() || #result.result().totalHits() == 0")
    public SearchOutcome searchOutcome(final String query, final int offset, final int limit) {
        try {
            final SearchRequest request = new SearchRequest(query).setOffset(offset).setLimit(limit);
            final SearchResult result = (SearchResult) booksIndex().search(request);
            final List<BookSearchDocument> hits = result.getHits().stream()
                .map(hit -> objectMapper.convertValue(hit, BookSearchDocument.class))
                .toList();
            final BookSearchResult page =
                new BookSearchResult(hits, result.getEstimatedTotalHits(), offset, limit);
            return new SearchOutcome(page, false);
        } catch (MeilisearchException ex) {
            LOG.warn("search.query failed, returning no results query={} ({})",
                LogSanitizer.forLog(query), ex.getClass().getSimpleName());
            return new SearchOutcome(new BookSearchResult(List.of(), 0, offset, limit), true);
        }
    }

    @Override
    public void index(final Collection<BookSearchDocument> documents) {
        if (documents.isEmpty()) {
            return;
        }
        try {
            final String json = objectMapper.writeValueAsString(documents);
            final Index index = booksIndex();
            index.waitForTask(index.addDocuments(json, BookSearchDocument.PRIMARY_KEY).getTaskUid());
        } catch (MeilisearchException ex) {
            throw new SearchIndexException("indexing " + documents.size() + " books failed", ex);
        }
    }

    @Override
    public void remove(final String bookId) {
        try {
            final Index index = booksIndex();
            index.waitForTask(index.deleteDocument(bookId).getTaskUid());
        } catch (MeilisearchException ex) {
            throw new SearchIndexException("removing book " + bookId + " from the index failed", ex);
        }
    }

    private Index booksIndex() {
        return client.index(props.indexName());
    }
}
