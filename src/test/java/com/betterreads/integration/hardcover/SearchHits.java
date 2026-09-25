package com.betterreads.integration.hardcover;

import tools.jackson.databind.node.ObjectNode;

final class SearchHits {

    private SearchHits() {
    }

    static ObjectNode picked(final ObjectNode search) {
        return (ObjectNode) search.at("/data/search/results/hits/1/document");
    }

    static void clear(final ObjectNode search) {
        ((ObjectNode) search.at("/data/search/results")).putArray("hits");
    }
}
