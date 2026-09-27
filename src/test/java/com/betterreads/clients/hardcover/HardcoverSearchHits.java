package com.betterreads.clients.hardcover;

import tools.jackson.databind.node.ObjectNode;

final class HardcoverSearchHits {

    private HardcoverSearchHits() {
    }

    static ObjectNode picked(final ObjectNode search) {
        return (ObjectNode) search.at("/data/search/results/hits/1/document");
    }
}
