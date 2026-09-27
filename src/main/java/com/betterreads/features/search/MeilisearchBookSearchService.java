package com.betterreads.features.search;

import com.betterreads.clients.meilisearch.MeilisearchProperties;
import com.betterreads.logging.LogSanitizer;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.SearchRequest;
import com.meilisearch.sdk.exceptions.MeilisearchException;
import com.meilisearch.sdk.model.MatchingStrategy;
import com.meilisearch.sdk.model.SearchResult;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Book search on Meilisearch.
 *
 * <p>A failed search returns an empty degraded result, a failed index throws.
 */
@Service
@RequiredArgsConstructor
class MeilisearchBookSearchService implements BookSearchService {

    private static final Logger LOG = LoggerFactory.getLogger(MeilisearchBookSearchService.class);

    /**
     * typo matches to another author pass the all-words strategy, "sanderson" fuzzy-matches
     * "Anderson" at ~0.03 against ~0.74 for the real author, so hits below this score are dropped
     */
    private static final double RANKING_SCORE_THRESHOLD = 0.4;

    private static final String BOOK_ID_FILTER = BookSearchDocument.PRIMARY_KEY + " = \"%s\"";

    private final Client client;

    private final MeilisearchProperties props;

    private final ObjectMapper objectMapper;

    @Override
    @Cacheable(cacheNames = SearchResultsCache.NAME, cacheManager = "searchCacheManager",
        unless = "#result.degraded() || #result.result().totalHits() == 0")
    public SearchOutcome search(final String query, final int offset, final int limit) {
        try {
            final SearchResult result = run(rankedRequest(query).setOffset(offset).setLimit(limit));
            final BookSearchResult page =
                new BookSearchResult(hits(result), result.getEstimatedTotalHits(), offset, limit);
            return new SearchOutcome(page, false);
        } catch (MeilisearchException ex) {
            LOG.warn("search.query failed, returning no results query={} ({})",
                LogSanitizer.forLog(query), ex.getClass().getSimpleName());
            return new SearchOutcome(new BookSearchResult(List.of(), 0, offset, limit), true);
        }
    }

    @Override
    public Optional<BookSearchDocument> hitFor(final String query, final String bookId) {
        try {
            final SearchRequest request = rankedRequest(query)
                .setLimit(1)
                .setFilter(new String[] {String.format(BOOK_ID_FILTER, bookId)});
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

    private static SearchRequest rankedRequest(final String query) {
        return new SearchRequest(query)
            .setMatchingStrategy(MatchingStrategy.ALL)
            .setRankingScoreThreshold(RANKING_SCORE_THRESHOLD);
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
