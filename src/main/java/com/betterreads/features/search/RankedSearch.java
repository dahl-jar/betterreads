package com.betterreads.features.search;

import com.meilisearch.sdk.SearchRequest;
import com.meilisearch.sdk.model.MatchingStrategy;

final class RankedSearch {

    /**
     * typo matches to another author pass the all-words strategy, "sanderson" fuzzy-matches
     * "Anderson" at ~0.03 against ~0.74 for the real author, so hits below this score are dropped
     */
    private static final double RANKING_SCORE_THRESHOLD = 0.4;

    private RankedSearch() {
    }

    static SearchRequest request(final String query) {
        return new SearchRequest(query)
            .setMatchingStrategy(MatchingStrategy.ALL)
            .setRankingScoreThreshold(RANKING_SCORE_THRESHOLD);
    }
}
