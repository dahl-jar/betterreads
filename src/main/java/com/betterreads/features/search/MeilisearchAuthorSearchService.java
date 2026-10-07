package com.betterreads.features.search;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import com.betterreads.clients.meilisearch.MeilisearchProperties;
import com.betterreads.logging.LogSanitizer;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.SearchRequest;
import com.meilisearch.sdk.exceptions.MeilisearchException;
import com.meilisearch.sdk.model.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
class MeilisearchAuthorSearchService implements AuthorSearchService {

    private static final Logger LOG = LoggerFactory.getLogger(MeilisearchAuthorSearchService.class);

    private static final int ID_PAGE_SIZE = 1000;

    private final Client client;

    private final MeilisearchProperties props;

    private final ObjectMapper objectMapper;

    MeilisearchAuthorSearchService(
        final Client client, final MeilisearchProperties props, final ObjectMapper objectMapper) {
        this.client = client;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Override
    @Cacheable(cacheNames = SearchResultsCache.AUTHORS_NAME, cacheManager = "searchCacheManager",
        unless = "#result.totalHits() == 0")
    public AuthorSearchResult search(final String query, final int offset, final int limit) {
        try {
            final SearchRequest request = RankedSearch.request(query)
                .setOffset(offset)
                .setLimit(limit);
            final SearchResult result = (SearchResult) authorsIndex().search(request);
            final List<AuthorSearchDocument> hits = result.getHits().stream()
                .map(hit -> objectMapper.convertValue(hit, AuthorSearchDocument.class))
                .toList();
            return new AuthorSearchResult(hits, result.getEstimatedTotalHits(), offset, limit);
        } catch (MeilisearchException ex) {
            LOG.warn("search.author-query failed, returning no results query={} ({})",
                LogSanitizer.forLog(query), ex.getClass().getSimpleName());
            return new AuthorSearchResult(List.of(), 0, offset, limit);
        }
    }

    @Override
    public void index(final Collection<AuthorSearchDocument> documents) {
        if (documents.isEmpty()) {
            return;
        }
        try {
            final Index index = authorsIndex();
            index.waitForTask(index.addDocuments(
                objectMapper.writeValueAsString(documents), AuthorSearchDocument.PRIMARY_KEY).getTaskUid());
        } catch (MeilisearchException ex) {
            throw new SearchIndexException("indexing authors failed count=" + documents.size(), ex);
        }
    }

    @Override
    public void deleteAll(final Collection<Long> authorIds) {
        if (authorIds.isEmpty()) {
            return;
        }
        try {
            final Index index = authorsIndex();
            final String ids = authorIds.stream().map(String::valueOf).collect(Collectors.joining(", "));
            index.waitForTask(index.deleteDocumentsByFilter(
                AuthorSearchDocument.PRIMARY_KEY + " IN [" + ids + "]").getTaskUid());
        } catch (MeilisearchException ex) {
            throw new SearchIndexException("removing authors from the index failed count=" + authorIds.size(), ex);
        }
    }

    @Override
    public boolean isEmpty() {
        try {
            return authorsIndex().getStats().getNumberOfDocuments() == 0;
        } catch (MeilisearchException ex) {
            throw new SearchIndexException("reading the authors index stats failed", ex);
        }
    }

    @Override
    public List<Long> indexedIds() {
        try {
            return IndexIds.all(authorsIndex(), objectMapper, AuthorSearchDocument.PRIMARY_KEY, ID_PAGE_SIZE)
                .map(JsonNode::asLong)
                .toList();
        } catch (MeilisearchException ex) {
            throw new SearchIndexException("reading the authors index ids failed", ex);
        }
    }

    private Index authorsIndex() {
        return client.index(props.authorsIndexName());
    }
}
