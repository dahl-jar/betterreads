package com.betterreads.features.search;

import com.betterreads.bookindex.BookIndexViewReader;
import com.betterreads.clients.meilisearch.MeilisearchProperties;
import com.betterreads.logging.LogSanitizer;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.SearchRequest;
import com.meilisearch.sdk.exceptions.MeilisearchException;
import com.meilisearch.sdk.model.SearchResult;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Book search on Meilisearch.
 *
 * <p>A failed search returns a degraded result with only the exact catalog matches, a failed index throws.
 */
@Service
@RequiredArgsConstructor
class MeilisearchBookSearchService implements BookSearchService {

    private static final Logger LOG = LoggerFactory.getLogger(MeilisearchBookSearchService.class);

    private final Client client;

    private final MeilisearchProperties props;

    private final ObjectMapper objectMapper;

    private final CatalogFallback fallback;

    private final BookIndexViewReader indexViews;

    private final BookSearchDocumentMapper mapper;

    @Override
    @Cacheable(cacheNames = SearchResultsCache.NAME, cacheManager = "searchCacheManager",
        unless = "#result.degraded() || #result.result().totalHits() == 0")
    public SearchOutcome search(final String query, final int offset, final int limit) {
        final SearchOutcome outcome = searchIndex(query, offset, limit);
        if (offset > 0 || !outcome.result().hits().isEmpty()) {
            return outcome;
        }
        final List<BookSearchDocument> found = indexViews.indexViewsByIds(fallback.find(query)).stream()
            .map(mapper::toDocument)
            .toList();
        if (found.isEmpty()) {
            return outcome;
        }
        if (!outcome.degraded()) {
            indexFound(found);
        }
        final List<BookSearchDocument> page = found.stream().limit(limit).toList();
        return new SearchOutcome(new BookSearchResult(page, found.size(), offset, limit), outcome.degraded());
    }

    @Override
    public Optional<BookSearchDocument> hitFor(final String query, final String bookId) {
        try {
            final SearchRequest request = RankedSearch.request(query)
                .setLimit(1)
                .setFilter(new String[] {BookSearchDocument.PRIMARY_KEY + " = " + IndexIds.quoted(bookId)});
            return hits(run(request)).stream().findFirst();
        } catch (MeilisearchException ex) {
            LOG.warn("search.hit-check failed query={} bookId={} ({})",
                LogSanitizer.forLog(query), LogSanitizer.forLog(bookId), ex.getClass().getSimpleName());
            return Optional.empty();
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
    public Set<String> indexedIds(final int pageSize) {
        try {
            return IndexIds.all(booksIndex(), objectMapper, BookSearchDocument.PRIMARY_KEY, pageSize)
                .map(JsonNode::asString)
                .collect(Collectors.toSet());
        } catch (MeilisearchException ex) {
            throw new SearchIndexException("reading the books index ids failed", ex);
        }
    }

    @Override
    public void deleteAll(final Collection<String> ids) {
        if (ids.isEmpty()) {
            return;
        }
        try {
            final Index index = booksIndex();
            index.waitForTask(index.deleteDocuments(new ArrayList<>(ids)).getTaskUid());
        } catch (MeilisearchException ex) {
            throw new SearchIndexException("deleting " + ids.size() + " documents failed", ex);
        }
    }

    private SearchOutcome searchIndex(final String query, final int offset, final int limit) {
        try {
            final SearchResult result = run(RankedSearch.request(query).setOffset(offset).setLimit(limit));
            final BookSearchResult page =
                new BookSearchResult(hits(result), result.getEstimatedTotalHits(), offset, limit);
            return new SearchOutcome(page, false);
        } catch (MeilisearchException ex) {
            LOG.warn("search.query failed, falling back to exact catalog matches query={} ({})",
                LogSanitizer.forLog(query), ex.getClass().getSimpleName());
            return new SearchOutcome(new BookSearchResult(List.of(), 0, offset, limit), true);
        }
    }

    private void indexFound(final List<BookSearchDocument> found) {
        try {
            final Set<String> present = IndexIds.among(booksIndex(), objectMapper, BookSearchDocument.PRIMARY_KEY,
                    found.stream().map(BookSearchDocument::bookId).toList())
                .map(JsonNode::asString)
                .collect(Collectors.toSet());
            index(found.stream().filter(document -> !present.contains(document.bookId())).toList());
        } catch (MeilisearchException | SearchIndexException ex) {
            LOG.warn("search.fallback indexing failed, the nightly reconcile retries ({})",
                ex.getClass().getSimpleName());
        }
    }

    private SearchResult run(final SearchRequest request) {
        return (SearchResult) booksIndex().search(request);
    }

    private List<BookSearchDocument> hits(final SearchResult result) {
        return result.getHits().stream()
            .map(hit -> objectMapper.convertValue(hit, BookSearchDocument.class))
            .toList();
    }

    private Index booksIndex() {
        return client.index(props.indexName());
    }
}
